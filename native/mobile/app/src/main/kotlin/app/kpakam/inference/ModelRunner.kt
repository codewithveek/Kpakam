package app.kpakam.inference

import app.kpakam.core.draft.DraftResponse
import app.kpakam.core.draft.DraftValidation
import app.kpakam.core.draft.DraftValidator
import app.kpakam.core.ledger.InferenceSource
import app.kpakam.core.prompts.PromptAssembler
import app.kpakam.core.routing.AiEvent
import app.kpakam.core.routing.Confidence
import app.kpakam.core.routing.Escalation
import app.kpakam.core.routing.EscalationStep
import app.kpakam.core.routing.EventSource
import app.kpakam.core.routing.QueueReason
import app.kpakam.core.routing.ReasonCode
import app.kpakam.core.routing.RouteDecision
import app.kpakam.core.routing.RouteTarget
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs a routed job on the chosen model, validates the reply, and escalates bad device
 * output (routing.md "Escalation"): retry once on device with the errors, then cloud
 * (`VALIDATION_ESCALATION`) or the queue. The trader never sees invalid data.
 */
@Singleton
class ModelRunner
    @Inject
    constructor(
        @param:OnDevice private val device: InferenceClient,
        @param:InCloud private val cloud: InferenceClient,
        @param:SafetyNet private val safetyNet: InferenceClient,
        private val prompts: PromptAssembler,
        private val validator: DraftValidator,
        private val rateLimiter: CloudRateLimiter,
        private val signals: RouteSignalsProvider,
    ) {
        /** Runs a text note according to [decision]. Never throws for model failures. */
        suspend fun runText(note: String, decision: RouteDecision): RunOutcome {
            val trace = TraceBuilder()
            return when (decision) {
                is RouteDecision.Device -> runOnDevice(note, decision, trace, attempt = 1, prompt = prompts.parse(note))
                is RouteDecision.Cloud -> runInCloud(cloudParse(note, decision.reason), trace)
                is RouteDecision.Queued -> RunOutcome.NeedsQueue(decision.reason, decision.queueReason, trace.queued())
            }
        }

        /** Sends a voice clip to the cloud model with the voice prompt; [reason] is SPEECH_CLOUD or SPEECH_MIXED. */
        suspend fun runVoice(clip: Attachment, reason: ReasonCode = ReasonCode.SPEECH_CLOUD): RunOutcome {
            val trace = TraceBuilder()
            if (!signals.cloudReachable) return RunOutcome.NeedsQueue(reason, blockedReason(), trace.queued())
            val job = CloudJob(InferenceRequest(prompts.voice(), clip), reason, prompts.voiceVersion)
            return runInCloud(job, trace)
        }

        /**
         * The safety net (routing.md): reading signals or routing threw, so make one call with the
         * `PREFER_ON_DEVICE` model. The output is validated as usual and marked low confidence.
         */
        suspend fun runSafetyNet(note: String): RunOutcome {
            val trace = TraceBuilder()
            val result = safetyNet.generate(InferenceRequest(prompts.parse(note)))
            val ranOn = (result as? InferenceResult.Success)?.source ?: EventSource.NONE
            trace.add(result, ranOn, if (ranOn == EventSource.ON_DEVICE) RouteTarget.DEVICE else RouteTarget.CLOUD)
            val text =
                (result as? InferenceResult.Success)?.text ?: return RunOutcome.Failed(
                    ReasonCode.ROUTER_FALLBACK,
                    (result as InferenceResult.Failure).error,
                    trace.build(valid = false),
                )
            return when (val validation = validator.validate(text)) {
                is DraftValidation.Valid -> {
                    val origin =
                        DraftOrigin(
                            journalSource(ranOn),
                            ReasonCode.ROUTER_FALLBACK,
                            prompts.parseVersion,
                            Confidence.LOW,
                        )
                    parsed(validation.response, text, origin, trace)
                }
                is DraftValidation.Invalid ->
                    RunOutcome.Failed(
                        ReasonCode.ROUTER_FALLBACK,
                        null,
                        trace.build(valid = false),
                    )
            }
        }

        private suspend fun runOnDevice(
            note: String,
            decision: RouteDecision.Device,
            trace: TraceBuilder,
            attempt: Int,
            prompt: String,
        ): RunOutcome {
            val result = device.generate(InferenceRequest(prompt))
            trace.add(result, EventSource.ON_DEVICE, RouteTarget.DEVICE)
            if (result is InferenceResult.Failure && result.error == InferenceError.DeviceUnavailable) {
                return deviceUnavailable(note, trace)
            }
            val text = (result as? InferenceResult.Success)?.text.orEmpty()
            val validation = validator.validate(text)
            if (validation is DraftValidation.Valid) {
                val origin =
                    DraftOrigin(InferenceSource.ON_DEVICE, decision.reason, prompts.parseVersion, decision.confidence)
                return parsed(validation.response, text, origin, trace)
            }
            val errors = (validation as DraftValidation.Invalid).errors
            val config = signals.appConfig
            return when (val step = Escalation.afterInvalidDeviceOutput(attempt, signals.online, config.cloudEnabled)) {
                EscalationStep.RetryOnDevice ->
                    runOnDevice(note, decision, trace, attempt + 1, prompts.retry(note, text, errors))
                EscalationStep.Cloud -> {
                    trace.escalated = true
                    runInCloud(cloudParse(note, ReasonCode.VALIDATION_ESCALATION), trace)
                }
                is EscalationStep.Queue -> {
                    trace.escalated = true
                    RunOutcome.NeedsQueue(ReasonCode.VALIDATION_ESCALATION, step.queueReason, trace.queued())
                }
            }
        }

        private suspend fun deviceUnavailable(note: String, trace: TraceBuilder): RunOutcome =
            if (signals.cloudReachable) {
                runInCloud(cloudParse(note, ReasonCode.DEVICE_UNAVAILABLE), trace)
            } else {
                RunOutcome.NeedsQueue(ReasonCode.DEVICE_UNAVAILABLE, blockedReason(), trace.queued())
            }

        private suspend fun runInCloud(job: CloudJob, trace: TraceBuilder): RunOutcome {
            if (!rateLimiter.tryAcquire()) {
                return RunOutcome.NeedsQueue(
                    job.reason,
                    QueueReason.RATE_LIMITED,
                    trace.queued(),
                )
            }
            val result = cloud.generate(job.request)
            trace.add(result, EventSource.IN_CLOUD, RouteTarget.CLOUD)
            return when (result) {
                is InferenceResult.Failure ->
                    when (result.error) {
                        InferenceError.QuotaExceeded ->
                            RunOutcome.NeedsQueue(
                                job.reason,
                                QueueReason.RATE_LIMITED,
                                trace.queued(),
                            )
                        InferenceError.Network -> RunOutcome.NeedsQueue(job.reason, blockedReason(), trace.queued())
                        else -> RunOutcome.Failed(job.reason, result.error, trace.build(valid = false))
                    }
                is InferenceResult.Success ->
                    when (val validation = validator.validate(result.text)) {
                        is DraftValidation.Valid -> {
                            val origin =
                                DraftOrigin(InferenceSource.IN_CLOUD, job.reason, job.promptVersion, Confidence.NORMAL)
                            parsed(validation.response, result.text, origin, trace)
                        }
                        is DraftValidation.Invalid -> RunOutcome.Failed(job.reason, null, trace.build(valid = false))
                    }
            }
        }

        private fun cloudParse(note: String, reason: ReasonCode) =
            CloudJob(InferenceRequest(prompts.parse(note)), reason, prompts.parseVersion)

        private fun parsed(response: DraftResponse, raw: String, origin: DraftOrigin, trace: TraceBuilder) =
            RunOutcome.Parsed(response, DraftValidator.stripCodeFence(raw), origin, trace.build(valid = true))

        private fun journalSource(ranOn: EventSource): InferenceSource =
            if (ranOn == EventSource.ON_DEVICE) InferenceSource.ON_DEVICE else InferenceSource.IN_CLOUD

        private fun blockedReason(): QueueReason =
            if (signals.online) QueueReason.CLOUD_DISABLED else QueueReason.OFFLINE

        private class CloudJob(val request: InferenceRequest, val reason: ReasonCode, val promptVersion: String)

        private class TraceBuilder {
            var attempts = 0
            var latencyMs = 0L
            var source = EventSource.NONE
            var target = RouteTarget.QUEUE
            var escalated = false

            fun add(result: InferenceResult, expected: EventSource, ranOn: RouteTarget) {
                attempts++
                latencyMs += result.latencyMs
                source = (result as? InferenceResult.Success)?.source ?: expected
                target = ranOn
            }

            fun build(valid: Boolean) =
                RunTrace(target, source, attempts.coerceAtMost(AiEvent.MAX_ATTEMPTS), latencyMs, escalated, valid)

            fun queued(): RunTrace = build(valid = false).copy(target = RouteTarget.QUEUE)
        }
    }

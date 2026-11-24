package app.kpakam.inference

import app.kpakam.core.ledger.InferenceSource
import app.kpakam.core.routing.Confidence
import app.kpakam.core.routing.EventSource
import app.kpakam.core.routing.QueueReason
import app.kpakam.core.routing.ReasonCode
import app.kpakam.core.routing.RouteDecision
import app.kpakam.core.routing.RouteTarget
import app.kpakam.inference.ScriptedClient.Companion.fail
import app.kpakam.inference.ScriptedClient.Companion.ok
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Escalation and the safety net, as routing.md describes them, with fake models. */
class ModelRunnerTest {
    private val valid = """{"drafts":[{"kind":"sale","item":"rice","totalNaira":45000,"confidence":0.9}]}"""
    private val invalid = """{"drafts":[{"kind":"gift","totalNaira":45000,"confidence":0.9}]}"""
    private val note = "sold 3 bags of rice 45k"
    private val deviceRoute = RouteDecision.Device(ReasonCode.DEFAULT_DEVICE)

    private fun runner(
        device: InferenceClient = ScriptedClient(EventSource.ON_DEVICE),
        cloud: InferenceClient = ScriptedClient(EventSource.IN_CLOUD),
        safetyNet: InferenceClient = ScriptedClient(EventSource.ON_DEVICE),
        online: Boolean = true,
        rateLimit: Int = 10,
    ): ModelRunner {
        val config = FakeConfig(Contract.config.copy(cloudRateLimitPerMinute = rateLimit))
        val signals =
            RouteSignalsProvider(config, FakeNetwork(online), {
                null
            }, FakeDeviceModel(), FakeSettings(), FakeSpeech, FakeSharedMic)
        return ModelRunner(
            device,
            cloud,
            safetyNet,
            Contract.prompts,
            Contract.validator,
            CloudRateLimiter(config, fixedClock),
            signals,
        )
    }

    @Test
    fun validDeviceOutputIsUsedAtOnce() =
        runTest {
            val outcome =
                runner(
                    device = ScriptedClient(EventSource.ON_DEVICE, ok(valid, EventSource.ON_DEVICE)),
                ).runText(note, deviceRoute)
            outcome as RunOutcome.Parsed
            assertEquals(InferenceSource.ON_DEVICE, outcome.origin.source)
            assertEquals(1, outcome.trace.attempts)
            assertEquals("parse-v2", outcome.origin.promptVersion)
        }

    @Test
    fun invalidDeviceOutputRetriesOnceWithTheErrors() =
        runTest {
            val device =
                ScriptedClient(
                    EventSource.ON_DEVICE,
                    ok(invalid, EventSource.ON_DEVICE),
                    ok(valid, EventSource.ON_DEVICE),
                )
            val outcome = runner(device = device).runText(note, deviceRoute)
            assertTrue(outcome is RunOutcome.Parsed)
            assertEquals(2, outcome.trace.attempts)
            assertTrue(device.prompts[1].contains("Your last JSON:\n$invalid"))
            assertTrue(device.prompts[1].contains("- drafts[0].kind must be one of"))
        }

    @Test
    fun twoInvalidDeviceOutputsEscalateToTheCloud() =
        runTest {
            val device =
                ScriptedClient(
                    EventSource.ON_DEVICE,
                    ok(invalid, EventSource.ON_DEVICE),
                    ok(invalid, EventSource.ON_DEVICE),
                )
            val cloud = ScriptedClient(EventSource.IN_CLOUD, ok(valid, EventSource.IN_CLOUD))
            val outcome = runner(device = device, cloud = cloud).runText(note, deviceRoute)
            outcome as RunOutcome.Parsed
            assertEquals(ReasonCode.VALIDATION_ESCALATION, outcome.origin.reason)
            assertEquals(InferenceSource.IN_CLOUD, outcome.origin.source)
            assertTrue(outcome.trace.escalated)
            assertEquals(3, outcome.trace.attempts)
            assertEquals(RouteTarget.CLOUD, outcome.trace.target)
        }

    @Test
    fun twoInvalidDeviceOutputsOfflineQueue() =
        runTest {
            val device =
                ScriptedClient(
                    EventSource.ON_DEVICE,
                    ok(invalid, EventSource.ON_DEVICE),
                    ok(invalid, EventSource.ON_DEVICE),
                )
            val outcome = runner(device = device, online = false).runText(note, deviceRoute)
            outcome as RunOutcome.NeedsQueue
            assertEquals(ReasonCode.VALIDATION_ESCALATION, outcome.reason)
            assertEquals(QueueReason.OFFLINE, outcome.queueReason)
            assertEquals(RouteTarget.QUEUE, outcome.trace.target)
        }

    @Test
    fun missingDeviceModelFallsBackToCloud() =
        runTest {
            val device = ScriptedClient(EventSource.ON_DEVICE, fail(InferenceError.DeviceUnavailable))
            val cloud = ScriptedClient(EventSource.IN_CLOUD, ok(valid, EventSource.IN_CLOUD))
            val outcome = runner(device = device, cloud = cloud).runText(note, deviceRoute)
            assertEquals(ReasonCode.DEVICE_UNAVAILABLE, (outcome as RunOutcome.Parsed).origin.reason)
        }

    @Test
    fun cloudQuotaQueuesAsRateLimited() =
        runTest {
            val cloud = ScriptedClient(EventSource.IN_CLOUD, fail(InferenceError.QuotaExceeded))
            val outcome = runner(cloud = cloud).runText(note, RouteDecision.Cloud(ReasonCode.LANGUAGE))
            assertEquals(QueueReason.RATE_LIMITED, (outcome as RunOutcome.NeedsQueue).queueReason)
        }

    @Test
    fun clientRateLimitQueuesTheSecondCloudCall() =
        runTest {
            val cloud = ScriptedClient(EventSource.IN_CLOUD, ok(valid, EventSource.IN_CLOUD))
            val runner = runner(cloud = cloud, rateLimit = 1)
            assertTrue(runner.runText(note, RouteDecision.Cloud(ReasonCode.LANGUAGE)) is RunOutcome.Parsed)
            val second = runner.runText(note, RouteDecision.Cloud(ReasonCode.LANGUAGE))
            assertEquals(QueueReason.RATE_LIMITED, (second as RunOutcome.NeedsQueue).queueReason)
        }

    @Test
    fun invalidCloudOutputFailsWithoutSavingAnything() =
        runTest {
            val cloud = ScriptedClient(EventSource.IN_CLOUD, ok("not json", EventSource.IN_CLOUD))
            val outcome = runner(cloud = cloud).runText(note, RouteDecision.Cloud(ReasonCode.TOO_LONG))
            assertTrue(outcome is RunOutcome.Failed)
            assertEquals(false, outcome.trace.valid)
        }

    @Test
    fun safetyNetMakesOneCallAndMarksLowConfidence() =
        runTest {
            val net = ScriptedClient(EventSource.ON_DEVICE, ok(valid, EventSource.IN_CLOUD))
            val outcome = runner(safetyNet = net).runSafetyNet(note)
            outcome as RunOutcome.Parsed
            assertEquals(ReasonCode.ROUTER_FALLBACK, outcome.origin.reason)
            assertEquals(Confidence.LOW, outcome.origin.confidence)
            assertEquals(InferenceSource.IN_CLOUD, outcome.origin.source)
            assertEquals(1, net.prompts.size)
        }
}

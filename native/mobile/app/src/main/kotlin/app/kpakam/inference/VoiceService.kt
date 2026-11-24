package app.kpakam.inference

import app.kpakam.core.ids.IdGenerator
import app.kpakam.core.language.LanguageScorer
import app.kpakam.core.routing.AiTask
import app.kpakam.core.routing.EventSource
import app.kpakam.core.routing.MixedSpeech
import app.kpakam.core.routing.ReasonCode
import app.kpakam.core.routing.RouteDecision
import app.kpakam.core.routing.RouteTarget
import app.kpakam.core.routing.SpeechRouter
import app.kpakam.core.routing.VoiceLanguage
import app.kpakam.inference.queue.AiJobQueue
import app.kpakam.platform.speech.DraftReader
import app.kpakam.platform.speech.LocalSpeechSource
import app.kpakam.platform.speech.MicLevel
import app.kpakam.platform.speech.SharedMic
import app.kpakam.platform.speech.SpeechTranscriber
import app.kpakam.platform.speech.TranscriptResult
import app.kpakam.platform.speech.VoiceClip
import app.kpakam.platform.speech.VoiceRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** What happened when the phone listened (SPEECH_DEVICE). */
sealed interface VoiceListen {
    /** The words can be trusted; the trader sees them in the text box before drafts are made. */
    data class Transcript(val text: String) : VoiceListen

    data object NothingHeard : VoiceListen

    /** Mixed languages heard (auto mode): the clip went to the cloud model, or waits in the queue. */
    data class SentClip(val outcome: ParseOutcome) : VoiceListen
}

/**
 * Use case: voice entry (F8, routing-v2). In auto or English mode with a local recogniser, the phone
 * listens; in auto mode it also keeps the clip, and if the mixed-speech check fails the clip goes to
 * the cloud model (SPEECH_MIXED). Pidgin/Yoruba mode, or no local recogniser, records a compressed
 * clip for cloud Gemini (SPEECH_CLOUD); offline, the clip is saved in the queue.
 */
@Singleton
class VoiceService
    @Inject
    constructor(
        private val signals: RouteSignalsProvider,
        private val runner: ModelRunner,
        private val parse: ParseService,
        private val transcriber: SpeechTranscriber,
        private val recorder: VoiceRecorder,
        private val sharedMic: SharedMic,
        private val reader: DraftReader,
        private val level: MicLevel,
        private val localSpeech: LocalSpeechSource,
        private val scorer: LanguageScorer,
        private val queue: AiJobQueue,
        private val ids: IdGenerator,
        private val clock: Clock,
    ) {
        /** Decides where the next voice entry goes (speech router). */
        suspend fun decide(): RouteDecision = SpeechRouter.route(signals.speech())

        /** The live microphone loudness (0..1) for the sound wave. */
        val micLevel: StateFlow<Float> get() = level.level

        /**
         * Listens on the phone; [onPartial] gets the words live. In auto mode it records the clip at the
         * same time and runs the mixed-speech check; [onSendingClip] runs when the clip has to be read
         * online instead. If the trader cancels, the clip is deleted.
         */
        suspend fun listenOnDevice(onPartial: (String) -> Unit, onSendingClip: () -> Unit): VoiceListen {
            val auto = signals.voiceLanguage == VoiceLanguage.AUTO
            val capture = if (auto) sharedMic.open(ids.next().value) else null
            var kept: VoiceClip? = null
            var cancelled = false
            val result =
                try {
                    transcriber.listen(capture, onPartial)
                } catch (e: CancellationException) {
                    cancelled = true
                    throw e
                } finally {
                    kept = withContext(NonCancellable) { capture?.finish() }
                    if (cancelled) withContext(NonCancellable) { kept?.let { delete(it.path) } }
                }
            val heard = result as? TranscriptResult.Heard
            val text = heard?.text.orEmpty()
            val threshold = signals.appConfig.routeLanguageThreshold
            val mixed = auto && MixedSpeech.isMixedSpeech(text, heard?.confidence, scorer.score(text), threshold)
            val clip = kept
            if (!mixed || clip == null) {
                clip?.let { delete(it.path) }
                return if (text.isBlank()) VoiceListen.NothingHeard else VoiceListen.Transcript(text)
            }
            onSendingClip()
            return VoiceListen.SentClip(sendOrQueue(clip, SpeechRouter.route(signals.speech(mixedHeard = true))))
        }

        /** Stops on-device listening early. */
        fun stopListening() = transcriber.stop()

        /** Stops recording and deletes the clip without sending it. */
        fun cancelRecording() = recorder.cancel()

        /** Starts recording a clip for the cloud model. [onLimit] runs at 30 seconds. */
        fun startRecording(onLimit: () -> Unit): Boolean = recorder.start(ids.next().value, onLimit)

        /** Stops recording and sends the clip (cloud) or saves it (queue), as [decision] says. */
        suspend fun finishRecording(decision: RouteDecision): ParseOutcome {
            val recorded = recorder.stop() ?: return ParseOutcome.Failed(ReasonCode.SPEECH_CLOUD)
            return sendOrQueue(recorded, decision)
        }

        /** Sends a queued clip. A clip cannot be re-heard on the device, so it always needs the cloud. */
        suspend fun runQueued(clip: VoiceClip, reason: ReasonCode, createdAtMs: Long): RunOutcome {
            val outcome = sendClip(clip, reason)
            parse.record(AiTask.PARSE_AUDIO, outcome, queuedMs = clock.millis() - createdAtMs)
            if (outcome !is RunOutcome.NeedsQueue) delete(clip.path)
            return outcome
        }

        /** Reads [text] aloud. */
        fun readAloud(text: String) = reader.speak(text)

        /** Downloads the on-device English recogniser at an idle moment, where Android allows it. */
        suspend fun prepareAtIdle() = localSpeech.installAtIdle()

        private suspend fun sendOrQueue(recorded: VoiceClip, decision: RouteDecision): ParseOutcome {
            if (decision is RouteDecision.Queued) {
                val jobId = queue.enqueueVoice(recorded, decision.reason, decision.queueReason)
                return ParseOutcome.Queued(jobId, decision.reason, decision.queueReason)
            }
            val outcome = sendClip(recorded, decision.reason)
            parse.record(AiTask.PARSE_AUDIO, outcome, queuedMs = null)
            return when (outcome) {
                is RunOutcome.Parsed -> {
                    delete(recorded.path)
                    ParseOutcome.Drafts(outcome.response, outcome.origin)
                }
                is RunOutcome.NeedsQueue -> {
                    val jobId = queue.enqueueVoice(recorded, outcome.reason, outcome.queueReason)
                    ParseOutcome.Queued(jobId, outcome.reason, outcome.queueReason)
                }
                is RunOutcome.Failed -> ParseOutcome.Failed(outcome.reason).also { delete(recorded.path) }
            }
        }

        private suspend fun sendClip(recorded: VoiceClip, reason: ReasonCode): RunOutcome {
            val bytes = withContext(Dispatchers.IO) { File(recorded.path).takeIf { it.exists() }?.readBytes() }
            if (bytes == null || bytes.isEmpty()) {
                val trace = RunTrace(RouteTarget.CLOUD, EventSource.NONE, 0, 0, escalated = false, valid = false)
                return RunOutcome.Failed(reason, null, trace)
            }
            return runner.runVoice(Attachment(bytes, recorded.mimeType), reason)
        }

        private suspend fun delete(path: String) = withContext(Dispatchers.IO) { File(path).delete() }
    }

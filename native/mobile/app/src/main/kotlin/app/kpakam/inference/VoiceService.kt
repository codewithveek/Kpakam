package app.kpakam.inference

import app.kpakam.core.ids.IdGenerator
import app.kpakam.core.routing.AiTask
import app.kpakam.core.routing.EventSource
import app.kpakam.core.routing.ReasonCode
import app.kpakam.core.routing.RouteDecision
import app.kpakam.core.routing.RouteTarget
import app.kpakam.core.routing.SpeechRouter
import app.kpakam.inference.queue.AiJobQueue
import app.kpakam.platform.speech.DraftReader
import app.kpakam.platform.speech.LocalSpeechSource
import app.kpakam.platform.speech.SpeechTranscriber
import app.kpakam.platform.speech.TranscriptResult
import app.kpakam.platform.speech.VoiceClip
import app.kpakam.platform.speech.VoiceRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case: voice entry (F8). English with a local recogniser is transcribed on the device
 * and then goes through the text router. Pidgin/Yoruba, or no local recogniser, records a
 * compressed clip for cloud Gemini; offline, the clip is saved in the queue.
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
        private val reader: DraftReader,
        private val localSpeech: LocalSpeechSource,
        private val queue: AiJobQueue,
        private val ids: IdGenerator,
        private val clock: Clock,
    ) {
        /** Decides where the next voice entry goes (speech router). */
        suspend fun decide(): RouteDecision = SpeechRouter.route(signals.speech())

        /** Listens with the on-device recogniser. The caller shows the transcript before parsing it. */
        suspend fun listenOnDevice(): TranscriptResult = transcriber.listen()

        /** Stops on-device listening early. */
        fun stopListening() = transcriber.stop()

        /** Starts recording a clip for the cloud model. [onLimit] runs at 30 seconds. */
        fun startRecording(onLimit: () -> Unit): Boolean = recorder.start(ids.next().value, onLimit)

        /** Stops recording and sends the clip (cloud) or saves it (queue), as [decision] says. */
        suspend fun finishRecording(decision: RouteDecision): ParseOutcome {
            val clip = recorder.stop() ?: return ParseOutcome.Failed(ReasonCode.SPEECH_CLOUD)
            if (decision is RouteDecision.Queued) {
                val jobId = queue.enqueueVoice(clip, decision.reason, decision.queueReason)
                return ParseOutcome.Queued(jobId, decision.reason, decision.queueReason)
            }
            val outcome = sendClip(clip)
            parse.record(AiTask.PARSE_AUDIO, outcome, queuedMs = null)
            return when (outcome) {
                is RunOutcome.Parsed -> ParseOutcome.Drafts(outcome.response, outcome.origin).also { delete(clip.path) }
                is RunOutcome.NeedsQueue -> {
                    val jobId = queue.enqueueVoice(clip, outcome.reason, outcome.queueReason)
                    ParseOutcome.Queued(jobId, outcome.reason, outcome.queueReason)
                }
                is RunOutcome.Failed -> ParseOutcome.Failed(outcome.reason).also { delete(clip.path) }
            }
        }

        /** Sends a queued clip. A clip cannot be re-heard on the device, so it always needs the cloud. */
        suspend fun runQueued(clip: VoiceClip, createdAtMs: Long): RunOutcome {
            val outcome = sendClip(clip)
            parse.record(AiTask.PARSE_AUDIO, outcome, queuedMs = clock.millis() - createdAtMs)
            if (outcome !is RunOutcome.NeedsQueue) delete(clip.path)
            return outcome
        }

        /** Reads [text] aloud. */
        fun readAloud(text: String) = reader.speak(text)

        /** Downloads the on-device English recogniser at an idle moment, where Android allows it. */
        suspend fun prepareAtIdle() = localSpeech.installAtIdle()

        private suspend fun sendClip(clip: VoiceClip): RunOutcome {
            val bytes = withContext(Dispatchers.IO) { File(clip.path).takeIf { it.exists() }?.readBytes() }
            if (bytes == null || bytes.isEmpty()) {
                val trace = RunTrace(RouteTarget.CLOUD, EventSource.NONE, 0, 0, escalated = false, valid = false)
                return RunOutcome.Failed(ReasonCode.SPEECH_CLOUD, null, trace)
            }
            return runner.runVoice(Attachment(bytes, clip.mimeType))
        }

        private suspend fun delete(path: String) = withContext(Dispatchers.IO) { File(path).delete() }
    }

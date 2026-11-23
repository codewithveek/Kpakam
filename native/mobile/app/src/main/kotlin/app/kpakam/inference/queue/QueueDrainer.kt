package app.kpakam.inference.queue

import app.kpakam.core.routing.AiTask
import app.kpakam.core.routing.QueueReason
import app.kpakam.inference.ParseService
import app.kpakam.inference.RunOutcome
import app.kpakam.inference.VoiceService
import app.kpakam.platform.speech.VoiceClip
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** What a drain run asks WorkManager to do next. */
sealed interface DrainResult {
    data object Done : DrainResult

    data object RetryLater : DrainResult
}

/** Drains the queue: each job is routed again and run (routing.md: "A queued job is routed again when it drains"). */
@Singleton
class QueueDrainer
    @Inject
    constructor(
        private val queue: AiJobQueue,
        private val parse: ParseService,
        private val voice: VoiceService,
    ) {
        private val mutex = Mutex()

        /** Runs every queued job once. Jobs held by data-saver wait for "Send now". */
        suspend fun drain(): DrainResult =
            mutex.withLock {
                queue.requeueInterrupted()
                var retry = false
                for (job in queue.queued()) {
                    if (!queue.markRunning(job.id)) continue
                    when (val outcome = run(job)) {
                        is RunOutcome.Parsed -> queue.succeed(job.id, outcome)
                        is RunOutcome.NeedsQueue -> {
                            queue.requeue(job.id, outcome.reason, outcome.queueReason)
                            retry = retry || outcome.queueReason != QueueReason.DATA_SAVER
                        }
                        is RunOutcome.Failed -> queue.fail(job.id)
                    }
                }
                if (retry) DrainResult.RetryLater else DrainResult.Done
            }

        private suspend fun run(job: AiJobEntity): RunOutcome {
            val audio = job.audioPath
            val mime = job.audioMime
            return if (job.task == AiTask.PARSE_AUDIO.wire && audio != null && mime != null) {
                voice.runQueued(VoiceClip(audio, mime, job.sizeBytes), job.createdAtMs)
            } else {
                parse.runQueued(job.inputText.orEmpty(), job.approved, job.createdAtMs)
            }
        }
    }

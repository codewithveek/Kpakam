package app.kpakam.inference.queue

import androidx.room.withTransaction
import app.kpakam.core.ids.IdGenerator
import app.kpakam.core.jobs.AiJobState
import app.kpakam.core.jobs.AiJobStateMachine
import app.kpakam.core.routing.AiTask
import app.kpakam.core.routing.QueueReason
import app.kpakam.core.routing.ReasonCode
import app.kpakam.inference.RunOutcome
import app.kpakam.platform.speech.VoiceClip
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Starts a queue drain when a connection exists. */
fun interface DrainTrigger {
    fun schedule()
}

/**
 * The pending AI job queue (Room). Every state change goes through the core
 * [AiJobStateMachine], so an illegal transition throws instead of corrupting a job.
 */
@Singleton
class AiJobQueue
    @Inject
    constructor(
        private val db: KpakamDatabase,
        private val ids: IdGenerator,
        private val clock: Clock,
        private val trigger: DrainTrigger,
    ) {
        private val dao get() = db.aiJobs()

        /** Jobs the trader still needs to see. */
        val open: Flow<List<AiJob>> = dao.observeOpen().map { rows -> rows.mapNotNull { it.toDomain() } }

        /** Saves a typed note for later. Returns the job id. */
        suspend fun enqueueText(note: String, reason: ReasonCode, queueReason: QueueReason): String =
            insert(AiTask.PARSE_TEXT, reason, queueReason) {
                it.copy(inputText = note, sizeBytes = note.toByteArray().size.toLong())
            }

        /** Saves a voice clip for later. Returns the job id. */
        suspend fun enqueueVoice(clip: VoiceClip, reason: ReasonCode, queueReason: QueueReason): String =
            insert(AiTask.PARSE_AUDIO, reason, queueReason) {
                it.copy(audioPath = clip.path, audioMime = clip.mimeType, sizeBytes = clip.sizeBytes)
            }

        /** Waiting jobs, oldest first. */
        suspend fun queued(): List<AiJobEntity> = dao.byState(AiJobState.QUEUED.name)

        /** Puts jobs that were running when the app stopped back in the queue. */
        suspend fun requeueInterrupted() {
            dao.byState(AiJobState.RUNNING.name).forEach { move(it.id, AiJobState.QUEUED) }
        }

        /** Marks a job as running. Returns `false` when it is no longer queued. */
        suspend fun markRunning(id: String): Boolean =
            db.withTransaction {
                val job = dao.get(id)
                if (job?.state != AiJobState.QUEUED.name) return@withTransaction false
                move(id, AiJobState.RUNNING) { it.copy(runs = it.runs + 1) }
                true
            }

        /** Stores drafts from a drained job; the trader checks them before anything is saved. */
        suspend fun succeed(id: String, outcome: RunOutcome.Parsed) =
            move(id, AiJobState.SUCCEEDED) {
                it.copy(
                    queueReason = null,
                    resultJson = outcome.rawJson,
                    resultSource = outcome.origin.source.wire,
                    resultReason = outcome.origin.reason.name,
                    resultPromptVersion = outcome.origin.promptVersion,
                    resultConfidence = outcome.origin.confidence.wire,
                )
            }

        /** Sends a running job back to the queue with a new reason. */
        suspend fun requeue(id: String, reason: ReasonCode, queueReason: QueueReason) =
            move(id, AiJobState.QUEUED) { it.copy(reason = reason.name, queueReason = queueReason.name) }

        /** Marks a running job as failed. */
        suspend fun fail(id: String) = move(id, AiJobState.FAILED)

        /** The trader tapped "Send now": approve the job and drain at once. */
        suspend fun approve(id: String) {
            db.withTransaction {
                dao
                    .get(
                        id,
                    )?.let { dao.update(it.copy(approved = true, updatedAtMs = clock.millis())) }
            }
            trigger.schedule()
        }

        /** The trader retries a failed job. */
        suspend fun retry(id: String) {
            move(id, AiJobState.QUEUED)
            trigger.schedule()
        }

        /** The trader checked (or discarded) a job's drafts; hide it. */
        suspend fun markReviewed(id: String) =
            db.withTransaction {
                dao.get(id)?.let { dao.update(it.copy(reviewed = true, updatedAtMs = clock.millis())) }
            }

        /**
         * The trader removes a waiting job. The state machine allows CANCELLED only from RUNNING,
         * so a queued job is claimed (QUEUED → RUNNING) and cancelled in one transaction.
         */
        suspend fun cancel(id: String) =
            db.withTransaction {
                val job = dao.get(id) ?: return@withTransaction
                when (AiJobState.valueOf(job.state)) {
                    AiJobState.QUEUED -> {
                        move(id, AiJobState.RUNNING)
                        move(id, AiJobState.CANCELLED)
                    }
                    AiJobState.RUNNING -> move(id, AiJobState.CANCELLED)
                    AiJobState.FAILED, AiJobState.SUCCEEDED, AiJobState.CANCELLED -> Unit
                }
                markReviewed(id)
            }

        private suspend fun insert(
            task: AiTask,
            reason: ReasonCode,
            queueReason: QueueReason,
            fill: (AiJobEntity) -> AiJobEntity,
        ): String {
            val id = ids.next().value
            val now = clock.millis()
            val blank =
                AiJobEntity(
                    id = id,
                    idempotencyKey = id,
                    task = task.wire,
                    state = AiJobState.QUEUED.name,
                    inputText = null,
                    audioPath = null,
                    audioMime = null,
                    sizeBytes = 0,
                    approved = false,
                    reason = reason.name,
                    queueReason = queueReason.name,
                    createdAtMs = now,
                    updatedAtMs = now,
                    runs = 0,
                    resultJson = null,
                    resultSource = null,
                    resultReason = null,
                    resultPromptVersion = null,
                    resultConfidence = null,
                    reviewed = false,
                )
            dao.insert(fill(blank))
            trigger.schedule()
            return id
        }

        private suspend fun move(id: String, to: AiJobState, change: (AiJobEntity) -> AiJobEntity = { it }) =
            db.withTransaction {
                val job = checkNotNull(dao.get(id)) { "No AI job $id" }
                val next = AiJobStateMachine.transition(AiJobState.valueOf(job.state), to)
                dao.update(change(job).copy(state = next.name, updatedAtMs = clock.millis()))
            }
    }

package app.kpakam.inference.queue

import app.kpakam.core.draft.DraftResponse
import app.kpakam.core.jobs.AiJobState
import app.kpakam.core.ledger.InferenceSource
import app.kpakam.core.routing.AiTask
import app.kpakam.core.routing.Confidence
import app.kpakam.core.routing.QueueReason
import app.kpakam.core.routing.ReasonCode
import app.kpakam.inference.DraftOrigin
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** A pending AI job as the UI and services see it. */
data class AiJob(
    val id: String,
    val task: AiTask,
    val state: AiJobState,
    val reason: ReasonCode,
    val queueReason: QueueReason?,
    val sizeBytes: Long,
    val createdAtMs: Long,
    val result: JobResult?,
)

/** Drafts produced by a job that drained from the queue, waiting for the trader to check. */
data class JobResult(val response: DraftResponse, val origin: DraftOrigin)

private val json = Json { ignoreUnknownKeys = false }

/** Converts a stored row to the domain model. Rows with unknown values are skipped. */
internal fun AiJobEntity.toDomain(): AiJob? {
    val taskValue = AiTask.entries.firstOrNull { it.wire == task } ?: return null
    return AiJob(
        id = id,
        task = taskValue,
        state = AiJobState.valueOf(state),
        reason = ReasonCode.valueOf(reason),
        queueReason = queueReason?.let(QueueReason::valueOf),
        sizeBytes = sizeBytes,
        createdAtMs = createdAtMs,
        result = storedResult(),
    )
}

private fun AiJobEntity.storedResult(): JobResult? {
    val raw = resultJson ?: return null
    val origin =
        DraftOrigin(
            source = resultSource?.let(InferenceSource::fromWire) ?: return null,
            reason = resultReason?.let(ReasonCode::valueOf) ?: return null,
            promptVersion = resultPromptVersion ?: return null,
            confidence = Confidence.entries.firstOrNull { it.wire == resultConfidence } ?: Confidence.NORMAL,
        )
    return try {
        JobResult(json.decodeFromString(DraftResponse.serializer(), raw), origin)
    } catch (e: SerializationException) {
        null
    }
}

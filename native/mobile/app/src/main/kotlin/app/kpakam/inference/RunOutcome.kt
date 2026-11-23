package app.kpakam.inference

import app.kpakam.core.draft.DraftResponse
import app.kpakam.core.ledger.InferenceSource
import app.kpakam.core.routing.Confidence
import app.kpakam.core.routing.EventSource
import app.kpakam.core.routing.QueueReason
import app.kpakam.core.routing.ReasonCode
import app.kpakam.core.routing.RouteTarget

/** What happened while running a job, for the `aiEvents` record. Holds no trader text. */
data class RunTrace(
    val target: RouteTarget,
    val source: EventSource,
    val attempts: Int,
    val latencyMs: Long,
    val escalated: Boolean,
    val valid: Boolean,
)

/** Where drafts came from; stored on each journal as `source`. */
data class DraftOrigin(
    val source: InferenceSource,
    val reason: ReasonCode,
    val promptVersion: String,
    val confidence: Confidence,
)

/** The result of running one AI job through the router and models. */
sealed interface RunOutcome {
    val reason: ReasonCode
    val trace: RunTrace

    /** Valid drafts. [rawJson] is the validated model reply, kept for queued jobs. */
    data class Parsed(
        val response: DraftResponse,
        val rawJson: String,
        val origin: DraftOrigin,
        override val trace: RunTrace,
    ) : RunOutcome {
        override val reason: ReasonCode get() = origin.reason
    }

    /** The job must wait in the queue. */
    data class NeedsQueue(
        override val reason: ReasonCode,
        val queueReason: QueueReason,
        override val trace: RunTrace,
    ) : RunOutcome

    /** The job cannot produce drafts (invalid cloud output, or the model refused). */
    data class Failed(
        override val reason: ReasonCode,
        val error: InferenceError?,
        override val trace: RunTrace,
    ) : RunOutcome
}

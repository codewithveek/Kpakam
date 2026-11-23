package app.kpakam.inference

import android.util.Log
import app.kpakam.core.draft.DraftResponse
import app.kpakam.core.ids.IdGenerator
import app.kpakam.core.language.LanguageScorer
import app.kpakam.core.routing.AiEvent
import app.kpakam.core.routing.AiTask
import app.kpakam.core.routing.QueueReason
import app.kpakam.core.routing.ReasonCode
import app.kpakam.core.routing.RouteDecision
import app.kpakam.core.routing.TextRouter
import app.kpakam.inference.events.AiEventRepository
import app.kpakam.inference.queue.AiJobQueue
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** What the trader sees after asking Kpakam to read a note. */
sealed interface ParseOutcome {
    /** Drafts to confirm. The list is empty when the model asks a question (`needsClarification`). */
    data class Drafts(val response: DraftResponse, val origin: DraftOrigin) : ParseOutcome

    /** The note waits in the queue and is read later. */
    data class Queued(val jobId: String, val reason: ReasonCode, val queueReason: QueueReason) : ParseOutcome

    /** The note could not be read. */
    data class Failed(val reason: ReasonCode) : ParseOutcome
}

/** Use case: turn a typed note (or an on-device transcript) into drafts (F1, F6, F7). */
@Singleton
class ParseService
    @Inject
    constructor(
        private val signals: RouteSignalsProvider,
        private val runner: ModelRunner,
        private val scorer: LanguageScorer,
        private val queue: AiJobQueue,
        private val events: AiEventRepository,
        private val ids: IdGenerator,
        private val clock: Clock,
    ) {
        /** Routes and runs [note] now; queues it when no model can run. */
        suspend fun parseNote(note: String): ParseOutcome {
            val outcome = runRouted(note, approved = false)
            record(AiTask.PARSE_TEXT, outcome, queuedMs = null)
            return when (outcome) {
                is RunOutcome.Parsed -> ParseOutcome.Drafts(outcome.response, outcome.origin)
                is RunOutcome.NeedsQueue -> {
                    val jobId = queue.enqueueText(note, outcome.reason, outcome.queueReason)
                    ParseOutcome.Queued(jobId, outcome.reason, outcome.queueReason)
                }
                is RunOutcome.Failed -> ParseOutcome.Failed(outcome.reason)
            }
        }

        /** Routes a queued note again and runs it. Used by the queue drainer. */
        suspend fun runQueued(note: String, approved: Boolean, createdAtMs: Long): RunOutcome {
            val outcome = runRouted(note, approved)
            record(AiTask.PARSE_TEXT, outcome, queuedMs = clock.millis() - createdAtMs)
            return outcome
        }

        /** Routes and runs [note]; if reading signals or routing throws, uses the safety net. */
        private suspend fun runRouted(note: String, approved: Boolean): RunOutcome {
            val decision =
                try {
                    route(note, approved)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Router failed; using the safety net", e)
                    null
                }
            return if (decision == null) runner.runSafetyNet(note) else runner.runText(note, decision)
        }

        private suspend fun route(note: String, approved: Boolean): RouteDecision {
            val input = signals.text(AiTask.PARSE_TEXT, note.length, scorer.score(note), approved)
            return TextRouter.route(input, signals.appConfig.routing)
        }

        private companion object {
            const val TAG = "ParseService"
        }

        /** Records an `aiEvents` entry. It holds the route and timing only, never the note. */
        fun record(task: AiTask, outcome: RunOutcome, queuedMs: Long?) {
            val trace = outcome.trace
            events.record(
                AiEvent(
                    id = ids.next(),
                    task = task,
                    target = trace.target,
                    reason = outcome.reason,
                    source = trace.source,
                    latencyMs = trace.latencyMs,
                    valid = trace.valid,
                    attempts = trace.attempts,
                    escalated = trace.escalated,
                    queuedMs = queuedMs?.coerceAtLeast(0),
                    promptVersion = (outcome as? RunOutcome.Parsed)?.origin?.promptVersion,
                ),
            )
        }
    }

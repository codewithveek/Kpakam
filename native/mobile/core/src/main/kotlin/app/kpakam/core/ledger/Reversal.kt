package app.kpakam.core.ledger

import app.kpakam.core.common.Outcome
import app.kpakam.core.common.map
import app.kpakam.core.ids.Ulid
import app.kpakam.core.time.LagosTime
import java.time.Instant

/** Everything needed to reverse one journal. */
public data class ReversalRequest(
    val original: Journal,
    /** `true` when a journal with `reverses == original.id` already exists. */
    val alreadyReversed: Boolean,
    val id: Ulid,
    val idempotencyKey: String,
    val occurredAt: Instant,
    val source: JournalSource,
)

/** Builds reversal journals. Corrections never edit or delete a journal. */
public object Reversal {
    private const val PREFIX = "Reversal: "

    /**
     * Copies the original lines in the same order with each sign flipped.
     *
     * Fails with [LedgerError.AlreadyReversed] when the original already has a
     * reversal. A reversal journal can itself be reversed.
     */
    public fun reverse(request: ReversalRequest): Outcome<Journal, LedgerError> {
        if (request.alreadyReversed) return Outcome.Err(LedgerError.AlreadyReversed)
        val original = request.original
        val lines = original.lines.map { JournalLine(it.account, -it.amount) }
        return Invariants.check(lines).map {
            Journal(
                id = request.id,
                idempotencyKey = request.idempotencyKey,
                occurredAt = LagosTime.occurredAt(request.occurredAt),
                day = LagosTime.dayOf(request.occurredAt),
                kind = JournalKind.REVERSAL,
                description = (PREFIX + original.description).take(JournalBuilder.MAX_DESCRIPTION),
                customerId = original.customerId,
                lines = lines,
                reverses = original.id,
                source = request.source,
            )
        }
    }
}

package app.kpakam.core.ledger

import app.kpakam.core.common.Outcome
import app.kpakam.core.common.flatMap
import app.kpakam.core.common.map
import app.kpakam.core.draft.Draft
import app.kpakam.core.draft.DraftKind
import app.kpakam.core.ids.CustomerId
import app.kpakam.core.ids.Ulid
import app.kpakam.core.money.Kobo
import app.kpakam.core.time.LagosTime
import java.math.BigDecimal
import java.time.Instant

/** A planned journal: its kind and its lines. */
private typealias Plan = Pair<JournalKind, List<JournalLine>>

/** Everything needed to turn one validated draft into a journal. */
public data class JournalRequest(
    val draft: Draft,
    val customerId: CustomerId?,
    val id: Ulid,
    val idempotencyKey: String,
    val occurredAt: Instant,
    val source: JournalSource,
)

/** Builds journals from drafts, exactly as the table in `ledger.md` says. */
public object JournalBuilder {
    /** The longest description the contract allows. */
    public const val MAX_DESCRIPTION: Int = 120

    /**
     * Builds the journal for [request].
     *
     * Fails with [LedgerError.AmountTooSmall], [LedgerError.PaidExceedsTotal],
     * [LedgerError.CustomerRequired], or (for a bug) [LedgerError.InvariantViolated].
     */
    public fun build(request: JournalRequest): Outcome<Journal, LedgerError> =
        plan(request.draft, request.customerId).flatMap { (kind, lines) ->
            Invariants.check(lines).map { toJournal(request, kind, lines) }
        }

    private fun toJournal(request: JournalRequest, kind: JournalKind, lines: List<JournalLine>): Journal =
        Journal(
            id = request.id,
            idempotencyKey = request.idempotencyKey,
            occurredAt = LagosTime.occurredAt(request.occurredAt),
            day = LagosTime.dayOf(request.occurredAt),
            kind = kind,
            description = describe(request.draft, kind),
            customerId = request.customerId,
            lines = lines,
            reverses = null,
            source = request.source,
        )

    private fun plan(draft: Draft, customer: CustomerId?): Outcome<Plan, LedgerError> {
        val total = Kobo.fromNaira(draft.totalNaira)
        if (total.isZero || total.value < 0) return Outcome.Err(LedgerError.AmountTooSmall)
        return when (draft.kind) {
            DraftKind.SALE -> Outcome.Ok(JournalKind.SALE to saleLines(total))
            DraftKind.EXPENSE ->
                Outcome.Ok(
                    JournalKind.EXPENSE to
                        listOf(JournalLine(Account.Expenses, total), JournalLine(Account.Cash, -total)),
                )
            DraftKind.REPAYMENT ->
                if (customer == null) {
                    Outcome.Err(LedgerError.CustomerRequired)
                } else {
                    val receivable = Account.Receivable(customer)
                    Outcome.Ok(
                        JournalKind.REPAYMENT to
                            listOf(JournalLine(Account.Cash, total), JournalLine(receivable, -total)),
                    )
                }
            DraftKind.CREDIT_SALE -> planCreditSale(total, Kobo.fromNaira(draft.paidNaira ?: 0.0), customer)
        }
    }

    private fun planCreditSale(
        total: Kobo,
        paid: Kobo,
        customer: CustomerId?,
    ): Outcome<Plan, LedgerError> {
        if (paid.value < 0) return Outcome.Err(LedgerError.AmountTooSmall)
        if (paid > total) return Outcome.Err(LedgerError.PaidExceedsTotal)
        if (paid == total) return Outcome.Ok(JournalKind.SALE to saleLines(total))
        if (customer == null) return Outcome.Err(LedgerError.CustomerRequired)
        val receivable = Account.Receivable(customer)
        val lines =
            if (paid.isZero) {
                listOf(JournalLine(receivable, total), JournalLine(Account.Sales, -total))
            } else {
                listOf(
                    JournalLine(Account.Cash, paid),
                    JournalLine(receivable, total - paid),
                    JournalLine(Account.Sales, -total),
                )
            }
        return Outcome.Ok(JournalKind.CREDIT_SALE to lines)
    }

    private fun saleLines(total: Kobo) = listOf(JournalLine(Account.Cash, total), JournalLine(Account.Sales, -total))

    /**
     * Returns the description: the non-empty parts `[quantity, unit, item]` joined by
     * single spaces, or the label of the journal [kind] when all parts are empty.
     * Trimmed to 120 characters.
     */
    public fun describe(draft: Draft, kind: JournalKind): String {
        val parts =
            listOfNotNull(draft.quantity?.let(::formatQuantity), draft.unit?.trim(), draft.item?.trim())
                .filter { it.isNotEmpty() }
        val text = parts.joinToString(" ").ifEmpty { kind.label }
        return text.take(MAX_DESCRIPTION)
    }

    /** Formats a quantity without trailing zeros: `3`, `2.5`. */
    public fun formatQuantity(quantity: Double): String =
        BigDecimal.valueOf(quantity).stripTrailingZeros().toPlainString()
}

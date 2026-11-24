package app.kpakam.core.ledger

import app.kpakam.core.draft.Draft
import app.kpakam.core.draft.DraftKind
import app.kpakam.core.money.Kobo
import java.math.BigDecimal

/** Turns a stored journal back into an editable draft, for "edit" (reversal plus a new entry). */
public object JournalDrafts {
    private const val FULL_CONFIDENCE = 1.0

    /**
     * Returns a draft that rebuilds the same lines as [journal], or `null` for a reversal
     * (a reversal can be undone, not edited). The description becomes the item, unless it
     * is only the kind label. [customerName] is the resolved customer's name, if any.
     */
    public fun toDraft(journal: Journal, customerName: String?): Draft? {
        val kind =
            when (journal.kind) {
                JournalKind.SALE -> DraftKind.SALE
                JournalKind.EXPENSE -> DraftKind.EXPENSE
                JournalKind.REPAYMENT -> DraftKind.REPAYMENT
                JournalKind.CREDIT_SALE -> DraftKind.CREDIT_SALE
                JournalKind.REVERSAL -> return null
            }
        val total =
            when (kind) {
                DraftKind.SALE, DraftKind.REPAYMENT -> journal.amountOf(Account.Cash)
                DraftKind.EXPENSE -> journal.amountOf(Account.Expenses)
                DraftKind.CREDIT_SALE -> -journal.amountOf(Account.Sales)
            }
        val paid = if (kind == DraftKind.CREDIT_SALE) toNaira(journal.amountOf(Account.Cash)) else null
        return Draft(
            kind = kind,
            item = journal.description.takeUnless { it == journal.kind.label },
            totalNaira = toNaira(total),
            paidNaira = paid,
            customerName = customerName,
            confidence = FULL_CONFIDENCE,
        )
    }

    /** Converts kobo to naira exactly (`1235` gives `12.35`). */
    public fun toNaira(kobo: Kobo): Double = BigDecimal.valueOf(kobo.value).movePointLeft(2).toDouble()

    private fun Journal.amountOf(account: Account): Kobo =
        lines.filter { it.account == account }.fold(Kobo.ZERO) { acc, line -> acc + line.amount }
}

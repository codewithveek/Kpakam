package app.kpakam.shared.ui

import app.kpakam.core.ledger.Account
import app.kpakam.core.ledger.Journal
import app.kpakam.core.ledger.JournalKind
import app.kpakam.core.money.Kobo
import app.kpakam.core.money.sumKobo

/** Undo and Fix for one entry. */
data class EntryActions(val onUndo: () -> Unit, val onFix: () -> Unit)

/** How an amount is shown: money in is green with "+", money out has "−", the rest is plain. */
enum class MoneyTone { Income, Expense, Plain }

/** Amount rules for entry rows, shared by Home, History and the customer page (web entry-amount.tsx). */
object EntryAmounts {
    /** The amount a trader thinks of for each kind, always positive. */
    fun headline(journal: Journal): Kobo {
        fun sum(match: (Account) -> Boolean) =
            journal.lines
                .filter { match(it.account) }
                .map { it.amount }
                .sumKobo()
        return when (journal.kind) {
            JournalKind.SALE, JournalKind.CREDIT_SALE -> -sum { it == Account.Sales }
            JournalKind.EXPENSE -> sum { it == Account.Expenses }
            JournalKind.REPAYMENT -> sum { it == Account.Cash }
            JournalKind.REVERSAL -> Kobo(journal.lines.sumOf { kotlin.math.abs(it.amount.value) } / 2)
        }
    }

    /** The part of a credit sale still owed (zero for other kinds). */
    fun creditOwed(journal: Journal): Kobo =
        if (journal.kind == JournalKind.CREDIT_SALE) {
            journal.lines
                .filter { it.account is Account.Receivable }
                .map { it.amount }
                .sumKobo()
        } else {
            Kobo.ZERO
        }

    /** The tone of an entry's amount. Undone entries are plain. */
    fun tone(journal: Journal, reversed: Boolean): MoneyTone =
        when {
            reversed -> MoneyTone.Plain
            journal.kind == JournalKind.EXPENSE -> MoneyTone.Expense
            journal.kind == JournalKind.REVERSAL -> MoneyTone.Plain
            else -> MoneyTone.Income
        }
}

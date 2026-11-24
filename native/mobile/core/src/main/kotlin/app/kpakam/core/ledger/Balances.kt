package app.kpakam.core.ledger

import app.kpakam.core.ids.CustomerId
import app.kpakam.core.ids.Ulid
import app.kpakam.core.money.Kobo
import app.kpakam.core.money.sumKobo

/** Totals for one Africa/Lagos day. */
public data class DayTotals(
    val sales: Kobo,
    val expenses: Kobo,
    val profit: Kobo,
    val debtCollected: Kobo,
) {
    public companion object {
        /** All totals zero. */
        public val EMPTY: DayTotals = DayTotals(Kobo.ZERO, Kobo.ZERO, Kobo.ZERO, Kobo.ZERO)
    }
}

/** Derived numbers. Cached balances must always equal these. */
public object Balances {
    private const val MAX_CHAIN = 64

    /** Customer balance: the sum of all `receivable:<customerId>` lines. Positive means they owe the trader. */
    public fun customerBalance(journals: Iterable<Journal>, customerId: CustomerId): Kobo {
        val account = Account.Receivable(customerId)
        return journals
            .flatMap { it.lines }
            .filter { it.account == account }
            .map { it.amount }
            .sumKobo()
    }

    /** Rebuilds every customer's balance from journal lines. */
    public fun allCustomerBalances(journals: Iterable<Journal>): Map<CustomerId, Kobo> =
        journals
            .flatMap { it.lines }
            .mapNotNull { line -> (line.account as? Account.Receivable)?.let { it.customerId to line.amount } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, amounts) -> amounts.sumKobo() }

    /**
     * Computes day totals over [journals] (already filtered to one day).
     *
     * debtCollected counts receivable lines of repayments and of reversals whose
     * chain of `reverses` leads back to a repayment; [lookup] finds a journal by
     * id (it may return `null` when the original is not loaded).
     */
    public fun dayTotals(journals: Iterable<Journal>, lookup: (Ulid) -> Journal?): DayTotals {
        val list = journals.toList()
        val sales = -list.sumOf(Account.Sales)
        val expenses = list.sumOf(Account.Expenses)
        val collected =
            -list
                .filter { rootKind(it, lookup) == JournalKind.REPAYMENT }
                .flatMap { it.lines }
                .filter { it.account is Account.Receivable }
                .map { it.amount }
                .sumKobo()
        return DayTotals(sales, expenses, sales - expenses, collected)
    }

    /** Follows `reverses` back to the first journal that is not a reversal and returns its kind. */
    public fun rootKind(journal: Journal, lookup: (Ulid) -> Journal?): JournalKind? {
        var current = journal
        repeat(MAX_CHAIN) {
            if (current.kind != JournalKind.REVERSAL) return current.kind
            current = current.reverses?.let(lookup) ?: return null
        }
        return null
    }

    private fun List<Journal>.sumOf(account: Account): Kobo =
        flatMap { it.lines }.filter { it.account == account }.map { it.amount }.sumKobo()
}

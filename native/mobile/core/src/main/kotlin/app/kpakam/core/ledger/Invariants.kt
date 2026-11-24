package app.kpakam.core.ledger

import app.kpakam.core.common.Outcome

/** The checks from `ledger.md` that run before every journal write. */
public object Invariants {
    private const val MIN_LINES = 2
    private const val MAX_LINES = 10
    private val ACCOUNT = Regex("^(cash|sales|expenses|receivable:[0-9A-HJKMNP-TV-Z]{26})$")

    /**
     * Checks [lines]: 2 to 10 lines, every amount a non-zero integer, a zero sum,
     * and valid account strings. Returns [LedgerError.InvariantViolated] on failure.
     */
    public fun check(lines: List<JournalLine>): Outcome<Unit, LedgerError> {
        val problem =
            when {
                lines.size !in MIN_LINES..MAX_LINES -> "a journal needs 2 to 10 lines, not ${lines.size}"
                lines.any { it.amount.isZero } -> "a line has a zero amount"
                lines.any { !ACCOUNT.matches(it.account.key) } -> "a line has an invalid account"
                sum(lines) != 0L -> "lines do not sum to zero"
                else -> null
            }
        return if (problem == null) Outcome.Ok(Unit) else Outcome.Err(LedgerError.InvariantViolated(problem))
    }

    private fun sum(lines: List<JournalLine>): Long =
        lines.fold(0L) { acc, line -> Math.addExact(acc, line.amount.value) }
}

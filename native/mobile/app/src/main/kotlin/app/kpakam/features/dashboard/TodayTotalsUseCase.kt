package app.kpakam.features.dashboard

import app.kpakam.core.ids.Ulid
import app.kpakam.core.ledger.Balances
import app.kpakam.core.ledger.DayTotals
import app.kpakam.core.ledger.Journal
import app.kpakam.core.ledger.JournalKind
import app.kpakam.features.ledger.JournalRecord
import app.kpakam.features.ledger.LedgerRepository
import app.kpakam.shared.time.LagosDates
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** One day's figures (F5), derived from journal lines and updated live. [records] are newest first. */
data class TodaySummary(val totals: DayTotals, val records: List<JournalRecord>) {
    /** Entries on this day that an Undo reversed. */
    val reversed: Set<Ulid> get() = records.mapNotNull { it.journal.reverses }.toSet()
}

/** Use case: a day's sales, expenses, profit, and debt collected. One Firestore query, served from cache offline. */
@Singleton
class TodayTotalsUseCase
    @Inject
    constructor(
        private val ledger: LedgerRepository,
        private val clock: Clock,
    ) {
        /** Live totals for today in Africa/Lagos. */
        fun today(): Flow<TodaySummary> = day(LagosDates.today(clock))

        /** Live totals and entries for [day] (`YYYY-MM-DD`, Africa/Lagos). */
        @OptIn(ExperimentalCoroutinesApi::class)
        fun day(day: String): Flow<TodaySummary> =
            ledger.journalsBetween(day, day).mapLatest { records ->
                val journals = records.map { it.journal }
                val known = loadOriginals(journals)
                TodaySummary(totals = Balances.dayTotals(journals) { id -> known[id] }, records = records)
            }

        /** Loads the originals that today's reversals point to, so an undone repayment nets out. */
        private suspend fun loadOriginals(journals: List<Journal>): Map<Ulid, Journal> {
            val known = journals.associateBy { it.id }.toMutableMap()
            var pending = journals.filter { it.kind == JournalKind.REVERSAL }.mapNotNull { it.reverses }
            repeat(MAX_DEPTH) {
                val missing = pending.filter { it !in known }
                if (missing.isEmpty()) return known
                val found = missing.mapNotNull { ledger.find(it) }
                found.forEach { known[it.id] = it }
                pending = found.filter { it.kind == JournalKind.REVERSAL }.mapNotNull { it.reverses }
            }
            return known
        }

        private companion object {
            const val MAX_DEPTH = 8
        }
    }

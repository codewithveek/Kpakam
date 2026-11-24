package app.kpakam.features.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kpakam.core.ids.CustomerId
import app.kpakam.core.ids.Ulid
import app.kpakam.core.ledger.DayTotals
import app.kpakam.features.customers.CustomerRepository
import app.kpakam.features.dashboard.TodayTotalsUseCase
import app.kpakam.shared.time.LagosDates
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import javax.inject.Inject

/** The history screen: one day at a time, newest first. */
data class HistoryUiState(
    val today: String,
    val days: List<String>,
    val selected: String,
    val totals: DayTotals = DayTotals.EMPTY,
    val records: List<JournalRecord> = emptyList(),
    val reversed: Set<Ulid> = emptySet(),
    val names: Map<CustomerId, String> = emptyMap(),
    val loading: Boolean = true,
)

/** History of the last two weeks, one day at a time, with that day's sales and expenses (F4). */
@HiltViewModel
class HistoryViewModel
    @Inject
    constructor(
        private val totals: TodayTotalsUseCase,
        customers: CustomerRepository,
        clock: Clock,
    ) : ViewModel() {
        private val days = LagosDates.lastDays(clock, DAYS)
        private val today = days.last()
        private val selected = MutableStateFlow(today)

        @OptIn(ExperimentalCoroutinesApi::class)
        val state: StateFlow<HistoryUiState> =
            combine(
                selected.flatMapLatest { day -> totals.day(day).map { day to it } },
                customers.customers.onStart { emit(emptyList()) },
            ) { (day, summary), customerList ->
                HistoryUiState(
                    today = today,
                    days = days,
                    selected = day,
                    totals = summary.totals,
                    records = summary.records,
                    reversed = summary.reversed,
                    names = customerList.associate { it.id to it.name },
                    loading = false,
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                HistoryUiState(today, days, today),
            )

        fun select(day: String) {
            if (day in days) selected.value = day
        }

        private companion object {
            const val DAYS = 14
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

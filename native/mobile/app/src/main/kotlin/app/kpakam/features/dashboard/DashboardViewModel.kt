package app.kpakam.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kpakam.core.ids.CustomerId
import app.kpakam.core.ids.Ulid
import app.kpakam.core.ledger.DayTotals
import app.kpakam.features.customers.CustomerRepository
import app.kpakam.features.ledger.JournalRecord
import app.kpakam.shared.time.LagosDates
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import javax.inject.Inject

/** The Home screen's numbers and latest entries. [loading] is true until the first result. */
data class DashboardUiState(
    val totals: DayTotals = DayTotals.EMPTY,
    val recent: List<JournalRecord> = emptyList(),
    val entries: Int = 0,
    val reversed: Set<Ulid> = emptySet(),
    val names: Map<CustomerId, String> = emptyMap(),
    val loading: Boolean = true,
    val today: String = "",
    val hour: Int = 0,
)

/** Today dashboard (F5) and the latest entries on Home. */
@HiltViewModel
class DashboardViewModel
    @Inject
    constructor(
        today: TodayTotalsUseCase,
        customers: CustomerRepository,
        clock: Clock,
    ) : ViewModel() {
        private val day = LagosDates.today(clock)
        private val hour = LagosDates.hour(clock)

        val state: StateFlow<DashboardUiState> =
            combine(
                today.today(),
                customers.customers.onStart { emit(emptyList()) },
            ) { summary, customerList ->
                DashboardUiState(
                    totals = summary.totals,
                    recent = summary.records.take(RECENT),
                    entries = summary.records.size,
                    reversed = summary.reversed,
                    names = customerList.associate { it.id to it.name },
                    loading = false,
                    today = day,
                    hour = hour,
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                DashboardUiState(today = day, hour = hour),
            )

        private companion object {
            const val RECENT = 5
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

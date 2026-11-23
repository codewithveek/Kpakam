package app.kpakam.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kpakam.core.ledger.DayTotals
import app.kpakam.core.time.LagosTime
import app.kpakam.inference.events.AiEventRepository
import app.kpakam.inference.events.AiStats
import app.kpakam.platform.network.ConnectivitySource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import javax.inject.Inject

/** The Today screen state. */
data class DashboardUiState(
    val totals: DayTotals = DayTotals.EMPTY,
    val entries: Int = 0,
    val pendingSync: Int = 0,
    val cloudCallsAvoided: Int = 0,
    val online: Boolean = true,
)

/** Today dashboard (F5) and the weekly "cloud calls avoided" counter (F6). */
@HiltViewModel
class DashboardViewModel
    @Inject
    constructor(
        today: TodayTotalsUseCase,
        events: AiEventRepository,
        network: ConnectivitySource,
        clock: Clock,
    ) : ViewModel() {
        val state: StateFlow<DashboardUiState> =
            combine(
                today.today(),
                events.statsSince(LagosTime.weekStart(clock.instant())).onStart { emit(AiStats.EMPTY) },
                network.state,
            ) { summary, stats, net ->
                DashboardUiState(
                    summary.totals,
                    summary.entries,
                    summary.pendingSync,
                    stats.avoidedCloudCalls,
                    net.online,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DashboardUiState())

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

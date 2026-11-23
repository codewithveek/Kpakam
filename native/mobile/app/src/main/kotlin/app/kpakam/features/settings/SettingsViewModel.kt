package app.kpakam.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kpakam.core.routing.ModelState
import app.kpakam.core.routing.VoiceLanguage
import app.kpakam.core.time.LagosTime
import app.kpakam.inference.DeviceModelSource
import app.kpakam.inference.events.AiEventRepository
import app.kpakam.inference.events.AiStats
import app.kpakam.platform.network.ConnectivitySource
import app.kpakam.platform.network.NetworkState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/** The settings screen state. */
data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val stats: AiStats = AiStats.EMPTY,
    val model: ModelState = ModelState.UNAVAILABLE,
    val network: NetworkState = NetworkState(online = true, metered = false),
)

/** Data control (F7), voice language (F8), and the trader's own numbers (F6). */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val repository: SettingsRepository,
        events: AiEventRepository,
        model: DeviceModelSource,
        network: ConnectivitySource,
        clock: Clock,
    ) : ViewModel() {
        val state: StateFlow<SettingsUiState> =
            combine(
                repository.settings,
                events.statsSince(LagosTime.weekStart(clock.instant())).onStart { emit(AiStats.EMPTY) },
                model.state,
                network.state,
            ) { settings, stats, modelState, net -> SettingsUiState(settings, stats, modelState, net) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

        fun setDataSaver(on: Boolean) = save { it.copy(dataSaver = on) }

        fun setVoiceLanguage(language: VoiceLanguage) = save { it.copy(voiceLanguage = language) }

        fun setAnalytics(on: Boolean) = save { it.copy(analyticsOptIn = on) }

        private fun save(change: (UserSettings) -> UserSettings) {
            viewModelScope.launch { repository.save(change(repository.settings.value)) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

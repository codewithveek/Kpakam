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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/** Feedback after "Download my records". */
sealed interface ExportMessage {
    data object Done : ExportMessage

    data object Failed : ExportMessage
}

/** The settings screen state. */
data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val stats: AiStats = AiStats.EMPTY,
    val model: ModelState = ModelState.UNAVAILABLE,
    val network: NetworkState = NetworkState(online = true, metered = false),
    val exportMessage: ExportMessage? = null,
)

/** Data control (F7), voice language (F8), the trader's own numbers (F6), and data export. */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val repository: SettingsRepository,
        private val exporter: ExportRecordsUseCase,
        events: AiEventRepository,
        model: DeviceModelSource,
        network: ConnectivitySource,
        clock: Clock,
    ) : ViewModel() {
        private val exportMessage = MutableStateFlow<ExportMessage?>(null)

        val state: StateFlow<SettingsUiState> =
            combine(
                repository.settings,
                events.statsSince(LagosTime.weekStart(clock.instant())).onStart { emit(AiStats.EMPTY) },
                model.state,
                network.state,
                exportMessage,
            ) { settings, stats, modelState, net, export -> SettingsUiState(settings, stats, modelState, net, export) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

        fun setDataSaver(on: Boolean) = save { it.copy(dataSaver = on) }

        fun setVoiceLanguage(language: VoiceLanguage) = save { it.copy(voiceLanguage = language) }

        fun setAnalytics(on: Boolean) = save { it.copy(analyticsOptIn = on) }

        /** Writes the trader's records to the document they picked. */
        fun export(uri: String) {
            viewModelScope.launch {
                exportMessage.value = if (exporter.export(uri)) ExportMessage.Done else ExportMessage.Failed
            }
        }

        fun dismissExportMessage() = exportMessage.update { null }

        private fun save(change: (UserSettings) -> UserSettings) {
            viewModelScope.launch { repository.save(change(repository.settings.value)) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

package app.kpakam.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kpakam.R
import app.kpakam.core.routing.VoiceLanguage
import app.kpakam.platform.firebase.AccountDeleter
import app.kpakam.platform.network.ConnectivitySource
import app.kpakam.shared.ui.ToastCenter
import app.kpakam.shared.ui.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The settings screen state. [deleting] is true while every record is being deleted. */
data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val deleting: Boolean = false,
)

/** Data control (F7), voice language (F8), data export and account deletion (PRD privacy). */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val repository: SettingsRepository,
        private val exporter: ExportRecordsUseCase,
        private val deleter: AccountDeleter,
        private val network: ConnectivitySource,
        private val toasts: ToastCenter,
    ) : ViewModel() {
        private val deleting = MutableStateFlow(false)

        val state: StateFlow<SettingsUiState> =
            combine(repository.settings, deleting) { settings, busy -> SettingsUiState(settings, busy) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

        fun setDataSaver(on: Boolean) = save { it.copy(dataSaver = on) }

        fun setVoiceLanguage(language: VoiceLanguage) = save { it.copy(voiceLanguage = language) }

        fun setAnalytics(on: Boolean) = save { it.copy(analyticsOptIn = on) }

        /** Writes the trader's records to the document they picked. */
        fun export(uri: String) {
            viewModelScope.launch {
                val done = exporter.export(uri)
                toasts.show(UiText.Res(if (done) R.string.settings_export_done else R.string.error_generic))
            }
        }

        /** Deletes every record and the account, after the trader typed DELETE. Needs the network. */
        fun deleteAccount() {
            if (deleting.value) return
            if (!network.state.value.online) {
                toasts.show(UiText.Res(R.string.settings_delete_needs_network))
                return
            }
            deleting.value = true
            viewModelScope.launch {
                val done = deleter.deleteEverything()
                deleting.value = false
                toasts.show(UiText.Res(if (done) R.string.settings_delete_done else R.string.error_generic))
            }
        }

        private fun save(change: (UserSettings) -> UserSettings) {
            viewModelScope.launch { repository.save(change(repository.settings.value)) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

package app.kpakam.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kpakam.platform.firebase.AuthGateway
import app.kpakam.platform.firebase.AuthState
import app.kpakam.platform.network.ConnectivitySource
import app.kpakam.shared.ui.ToastCenter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Exposes the sign-in state, the network status pill, and the app's toasts to the root composable. */
@HiltViewModel
class RootViewModel
    @Inject
    constructor(
        private val gateway: AuthGateway,
        network: ConnectivitySource,
        val toasts: ToastCenter,
    ) : ViewModel() {
        val auth: StateFlow<AuthState> = gateway.state

        val online: StateFlow<Boolean> =
            network.state
                .map { it.online }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), network.state.value.online)

        fun retrySignIn() = gateway.start()

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

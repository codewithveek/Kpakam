package app.kpakam.navigation

import androidx.lifecycle.ViewModel
import app.kpakam.platform.firebase.AuthGateway
import app.kpakam.platform.firebase.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Exposes the sign-in state to the root composable. */
@HiltViewModel
class RootViewModel
    @Inject
    constructor(private val gateway: AuthGateway) : ViewModel() {
        val auth: StateFlow<AuthState> = gateway.state

        fun retrySignIn() = gateway.start()
    }

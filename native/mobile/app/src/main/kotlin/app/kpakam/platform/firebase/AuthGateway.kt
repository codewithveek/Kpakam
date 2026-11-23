package app.kpakam.platform.firebase

import app.kpakam.di.AppScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Sign-in state. Anonymous sign-in needs the network once; after that the uid is cached. */
sealed interface AuthState {
    data object Starting : AuthState

    data class SignedIn(val uid: String) : AuthState

    data object NeedsNetwork : AuthState
}

/** Signs the trader in anonymously on first launch. */
@Singleton
class AuthGateway
    @Inject
    constructor(
        private val auth: FirebaseAuth,
        @param:AppScope private val scope: CoroutineScope,
    ) {
        private val mutableState = MutableStateFlow<AuthState>(AuthState.Starting)
        val state: StateFlow<AuthState> = mutableState.asStateFlow()

        /** Starts sign-in in the background. Safe to call again after a failure. */
        fun start() {
            scope.launch { signIn() }
        }

        /** Suspends until a uid exists. */
        suspend fun awaitUid(): String = state.filterIsInstance<AuthState.SignedIn>().first().uid

        private suspend fun signIn() {
            auth.currentUser?.let {
                mutableState.value = AuthState.SignedIn(it.uid)
                return
            }
            mutableState.value = AuthState.Starting
            mutableState.value =
                try {
                    auth
                        .signInAnonymously()
                        .await()
                        .user
                        ?.uid
                        ?.let { AuthState.SignedIn(it) } ?: AuthState.NeedsNetwork
                } catch (e: FirebaseException) {
                    AuthState.NeedsNetwork
                }
        }
    }

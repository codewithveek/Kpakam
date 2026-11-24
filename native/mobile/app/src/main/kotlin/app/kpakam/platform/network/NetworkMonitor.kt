package app.kpakam.platform.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import app.kpakam.di.AppScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** Connection state. [online] means a validated internet connection, not only a radio link. */
data class NetworkState(val online: Boolean, val metered: Boolean)

/** Where services read the connection state. */
interface ConnectivitySource {
    val state: StateFlow<NetworkState>
}

/** ConnectivityManager adapter. Mobile data is normal; [NetworkState.metered] is only shown, never waited on. */
@Singleton
class NetworkMonitor
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @AppScope scope: CoroutineScope,
    ) : ConnectivitySource {
        private val manager = context.getSystemService(ConnectivityManager::class.java)

        override val state: StateFlow<NetworkState> =
            callbackFlow {
                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            trySend(current())
                        }

                        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                            trySend(current())
                        }

                        override fun onLost(network: Network) {
                            trySend(current())
                        }
                    }
                manager.registerDefaultNetworkCallback(callback)
                trySend(current())
                awaitClose { manager.unregisterNetworkCallback(callback) }
            }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, current())

        private fun current(): NetworkState {
            val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
            val online =
                capabilities != null &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            return NetworkState(online = online, metered = manager.isActiveNetworkMetered)
        }
    }

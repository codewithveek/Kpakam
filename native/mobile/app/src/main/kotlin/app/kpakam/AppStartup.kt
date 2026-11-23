package app.kpakam

import app.kpakam.di.AppScope
import app.kpakam.features.settings.SettingsRepository
import app.kpakam.inference.DeviceModelManager
import app.kpakam.inference.VoiceService
import app.kpakam.inference.queue.DrainTrigger
import app.kpakam.platform.firebase.AuthGateway
import app.kpakam.platform.firebase.AuthState
import app.kpakam.platform.firebase.RemoteConfigSource
import app.kpakam.platform.network.ConnectivitySource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Work that starts once per process, after Firebase is configured. */
@Singleton
class AppStartup
    @Inject
    constructor(
        private val auth: AuthGateway,
        private val remoteConfig: RemoteConfigSource,
        private val settings: SettingsRepository,
        private val deviceModel: DeviceModelManager,
        private val voice: VoiceService,
        private val network: ConnectivitySource,
        private val drain: DrainTrigger,
        @param:AppScope private val scope: CoroutineScope,
    ) {
        fun start() {
            remoteConfig.start()
            auth.start()
            drain.schedule()
            scope.launch {
                auth.state.filterIsInstance<AuthState.SignedIn>().first()
                settings.ensureProfile()
            }
            scope.launch {
                network.state.filter { it.online }.collect { state ->
                    deviceModel.prepare(unmetered = !state.metered)
                    drain.schedule()
                }
            }
            scope.launch { voice.prepareAtIdle() }
        }
    }

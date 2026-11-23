package app.kpakam.inference

import app.kpakam.core.config.AppConfig
import app.kpakam.core.routing.AiTask
import app.kpakam.core.routing.SpeechRouteSignals
import app.kpakam.core.routing.TextRouteSignals
import app.kpakam.features.settings.SettingsRepository
import app.kpakam.platform.battery.BatterySource
import app.kpakam.platform.firebase.ConfigSource
import app.kpakam.platform.network.ConnectivitySource
import app.kpakam.platform.speech.LocalSpeechSource
import javax.inject.Inject
import javax.inject.Singleton

/** Collects the router's inputs from the platform at the moment of a request. */
@Singleton
class RouteSignalsProvider
    @Inject
    constructor(
        private val config: ConfigSource,
        private val network: ConnectivitySource,
        private val battery: BatterySource,
        private val deviceModel: DeviceModelSource,
        private val settings: SettingsRepository,
        private val localSpeech: LocalSpeechSource,
    ) {
        /** The current Remote Config values. */
        val appConfig: AppConfig get() = config.config.value

        /** `online && cloudEnabled`, as routing.md defines it. */
        val cloudReachable: Boolean get() = network.state.value.online && appConfig.cloudEnabled

        /** `true` while the phone has a validated connection. */
        val online: Boolean get() = network.state.value.online

        /** Signals for the text and image router. Refreshes the on-device model state first. */
        suspend fun text(task: AiTask, chars: Int, languageScore: Double, approved: Boolean): TextRouteSignals =
            TextRouteSignals(
                task = task,
                chars = chars,
                languageScore = languageScore,
                online = network.state.value.online,
                cloudEnabled = appConfig.cloudEnabled,
                dataSaver = settings.settings.value.dataSaver,
                approved = approved,
                deviceModel = deviceModel.refresh(),
                battery = battery.current(),
            )

        /** Signals for the speech router. */
        suspend fun speech(): SpeechRouteSignals =
            SpeechRouteSignals(
                voiceLanguage = settings.settings.value.voiceLanguage,
                localSpeech = localSpeech.englishState(),
                online = network.state.value.online,
                cloudEnabled = appConfig.cloudEnabled,
            )
    }

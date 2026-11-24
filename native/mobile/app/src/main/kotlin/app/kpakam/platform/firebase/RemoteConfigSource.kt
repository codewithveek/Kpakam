package app.kpakam.platform.firebase

import android.util.Log
import app.kpakam.BuildConfig
import app.kpakam.R
import app.kpakam.core.config.AppConfig
import app.kpakam.di.AppScope
import app.kpakam.platform.assets.ContractAssets
import com.google.firebase.FirebaseException
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** The app's configuration. Defaults are bundled, so it is never empty, even offline on first launch. */
interface ConfigSource {
    val config: StateFlow<AppConfig>
}

/** Remote Config adapter: bundled defaults, background fetch, and real-time updates. */
@Singleton
class RemoteConfigSource
    @Inject
    constructor(
        private val remoteConfig: FirebaseRemoteConfig,
        private val assets: ContractAssets,
        @param:AppScope private val scope: CoroutineScope,
    ) : ConfigSource {
        private val mutableConfig = MutableStateFlow(assets.defaultConfig)
        override val config: StateFlow<AppConfig> = mutableConfig.asStateFlow()

        /** Activates cached values now and fetches new ones in the background. */
        fun start() {
            scope.launch {
                try {
                    val interval = if (BuildConfig.DEBUG) 0L else FETCH_INTERVAL_SECONDS
                    remoteConfig
                        .setConfigSettingsAsync(remoteConfigSettings { minimumFetchIntervalInSeconds = interval })
                        .await()
                    remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults).await()
                    remoteConfig.activate().await()
                    publish()
                    remoteConfig.fetchAndActivate().await()
                    publish()
                } catch (e: FirebaseException) {
                    Log.i(TAG, "Remote Config fetch failed; using cached or bundled values", e)
                }
            }
            remoteConfig.addOnConfigUpdateListener(
                object : ConfigUpdateListener {
                    override fun onUpdate(configUpdate: ConfigUpdate) {
                        remoteConfig.activate().addOnSuccessListener { publish() }
                    }

                    override fun onError(error: FirebaseRemoteConfigException) {
                        Log.i(TAG, "Remote Config real-time update failed", error)
                    }
                },
            )
        }

        private fun publish() {
            val defaults = assets.defaultValues
            mutableConfig.value =
                try {
                    AppConfig.fromValues { key ->
                        remoteConfig.getValue(key).asString().ifEmpty { defaults[key] }
                    }
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Bad Remote Config value; keeping bundled defaults", e)
                    assets.defaultConfig
                }
        }

        private companion object {
            const val TAG = "RemoteConfig"
            const val FETCH_INTERVAL_SECONDS = 3600L
        }
    }

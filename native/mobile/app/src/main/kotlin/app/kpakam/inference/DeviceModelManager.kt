package app.kpakam.inference

import android.util.Log
import app.kpakam.core.routing.ModelState
import app.kpakam.di.AppScope
import app.kpakam.platform.firebase.ConfigSource
import com.google.firebase.ai.DownloadStatus
import com.google.firebase.ai.OnDeviceExtension
import com.google.firebase.ai.OnDeviceModelStatus
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.PublicPreviewAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** Where services read the on-device model state. */
interface DeviceModelSource {
    val state: StateFlow<ModelState>

    /** Checks the model again and returns the fresh state. */
    suspend fun refresh(): ModelState
}

/** Checks, downloads, and warms up Gemini Nano through the model's `onDeviceExtension`. */
@OptIn(PublicPreviewAPI::class)
@Singleton
class DeviceModelManager
    @Inject
    constructor(
        private val models: FirebaseModels,
        private val config: ConfigSource,
        @param:AppScope private val scope: CoroutineScope,
    ) : DeviceModelSource {
        private val mutableState = MutableStateFlow(ModelState.UNAVAILABLE)
        override val state: StateFlow<ModelState> = mutableState.asStateFlow()

        override suspend fun refresh(): ModelState {
            val fresh =
                try {
                    extension()?.checkStatus()?.let(::map) ?: ModelState.UNAVAILABLE
                } catch (e: CancellationException) {
                    throw e
                } catch (e: FirebaseAIException) {
                    Log.i(TAG, "On-device model check failed", e)
                    ModelState.UNAVAILABLE
                }
            mutableState.value = fresh
            return fresh
        }

        /** Checks the model; downloads it on an unmetered connection; warms it up when ready. */
        fun prepare(unmetered: Boolean) {
            scope.launch {
                when (refresh()) {
                    ModelState.AVAILABLE -> warmUp()
                    ModelState.DOWNLOADABLE -> if (unmetered) download()
                    ModelState.DOWNLOADING, ModelState.UNAVAILABLE -> Unit
                }
            }
        }

        private suspend fun download() {
            val extension = extension() ?: return
            extension.download().collect { status ->
                when (status) {
                    is DownloadStatus.DownloadStarted, is DownloadStatus.DownloadInProgress ->
                        mutableState.value = ModelState.DOWNLOADING
                    is DownloadStatus.DownloadCompleted -> if (refresh() == ModelState.AVAILABLE) warmUp()
                    is DownloadStatus.DownloadFailed -> refresh()
                    else -> Unit
                }
            }
        }

        private suspend fun warmUp() {
            try {
                extension()?.warmUp()
            } catch (e: FirebaseAIException) {
                Log.i(TAG, "Warm-up failed", e)
            }
        }

        /** The on-device extension of the `ONLY_ON_DEVICE` model; `null` when firebase-ai-ondevice is missing. */
        private fun extension(): OnDeviceExtension? =
            models
                .device(
                    config.config.value.cloudModelName,
                ).onDeviceExtension

        private fun map(status: OnDeviceModelStatus): ModelState =
            when (status) {
                OnDeviceModelStatus.AVAILABLE -> ModelState.AVAILABLE
                OnDeviceModelStatus.DOWNLOADABLE -> ModelState.DOWNLOADABLE
                OnDeviceModelStatus.DOWNLOADING -> ModelState.DOWNLOADING
                else -> ModelState.UNAVAILABLE
            }

        private companion object {
            const val TAG = "DeviceModel"
        }
    }

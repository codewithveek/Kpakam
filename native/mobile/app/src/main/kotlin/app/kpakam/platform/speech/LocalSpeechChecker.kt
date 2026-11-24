package app.kpakam.platform.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import app.kpakam.core.routing.ModelState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Where services read whether English speech can be recognised on the device. */
interface LocalSpeechSource {
    /** The on-device English recogniser state, as the speech router expects it. */
    suspend fun englishState(): ModelState

    /** Asks the system to download the on-device English model, where Android supports it. */
    suspend fun installAtIdle()
}

/**
 * Checks the on-device recogniser. Android 13+ reports installed, pending, and supported
 * languages; Android 12 only says whether on-device recognition exists; older phones
 * have no reliable offline check, so they report [ModelState.UNAVAILABLE] and use the cloud.
 */
@Singleton
class LocalSpeechChecker
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : LocalSpeechSource {
        override suspend fun englishState(): ModelState =
            withContext(Dispatchers.Main) {
                when {
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> ModelState.UNAVAILABLE
                    !SpeechRecognizer.isOnDeviceRecognitionAvailable(context) -> ModelState.UNAVAILABLE
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> checkSupport()
                    else -> ModelState.AVAILABLE
                }
            }

        override suspend fun installAtIdle() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
            if (englishState() != ModelState.DOWNLOADABLE) return
            withContext(Dispatchers.Main) {
                val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                recognizer.triggerModelDownload(englishIntent())
                recognizer.destroy()
            }
        }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private suspend fun checkSupport(): ModelState {
            val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            return try {
                suspendCancellableCoroutine { continuation ->
                    val executor = Executor { it.run() }
                    recognizer.checkRecognitionSupport(
                        englishIntent(),
                        executor,
                        object : RecognitionSupportCallback {
                            override fun onSupportResult(support: RecognitionSupport) {
                                continuation.resume(map(support))
                            }

                            override fun onError(error: Int) {
                                continuation.resume(ModelState.UNAVAILABLE)
                            }
                        },
                    )
                }
            } finally {
                recognizer.destroy()
            }
        }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private fun map(support: RecognitionSupport): ModelState {
            fun List<String>.hasEnglish() = any { it.startsWith(ENGLISH_PREFIX, ignoreCase = true) }
            return when {
                support.installedOnDeviceLanguages.hasEnglish() -> ModelState.AVAILABLE
                support.pendingOnDeviceLanguages.hasEnglish() -> ModelState.DOWNLOADING
                support.supportedOnDeviceLanguages.hasEnglish() -> ModelState.DOWNLOADABLE
                else -> ModelState.UNAVAILABLE
            }
        }

        companion object {
            const val LANGUAGE_TAG = "en-US"
            private const val ENGLISH_PREFIX = "en"

            /** The recogniser intent for English, preferring offline recognition. */
            fun englishIntent(): Intent =
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE_TAG)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
        }
    }

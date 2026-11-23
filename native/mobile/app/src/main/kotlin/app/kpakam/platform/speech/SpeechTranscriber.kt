package app.kpakam.platform.speech

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** The result of on-device speech recognition. */
sealed interface TranscriptResult {
    data class Heard(val text: String) : TranscriptResult

    data object NothingHeard : TranscriptResult

    data class Failed(val code: Int) : TranscriptResult
}

/** Turns English speech into text with the on-device recogniser (SPEECH_DEVICE). */
interface SpeechTranscriber {
    /** Listens for up to 30 seconds and returns the transcript. */
    suspend fun listen(): TranscriptResult

    /** Stops listening early; [listen] then returns what it heard. */
    fun stop()
}

/** SpeechRecognizer adapter. Must run on the main thread, which [listen] ensures. */
@Singleton
class AndroidSpeechTranscriber
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : SpeechTranscriber {
        private val handler = Handler(Looper.getMainLooper())
        private var recognizer: SpeechRecognizer? = null

        override suspend fun listen(): TranscriptResult =
            withContext(Dispatchers.Main) {
                val created = create()
                recognizer = created
                try {
                    suspendCancellableCoroutine { continuation ->
                        created.setRecognitionListener(Listener { continuation.resume(it) })
                        created.startListening(LocalSpeechChecker.englishIntent())
                        handler.postDelayed({ created.stopListening() }, MAX_LISTEN_MS)
                        continuation.invokeOnCancellation { handler.post { created.cancel() } }
                    }
                } finally {
                    handler.removeCallbacksAndMessages(null)
                    created.destroy()
                    recognizer = null
                }
            }

        override fun stop() {
            handler.post { recognizer?.stopListening() }
        }

        private fun create(): SpeechRecognizer =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }

        private class Listener(private val done: (TranscriptResult) -> Unit) : RecognitionListener {
            private var finished = false

            private fun finish(result: TranscriptResult) {
                if (!finished) {
                    finished = true
                    done(result)
                }
            }

            override fun onResults(results: Bundle) {
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                finish(if (text.isNullOrEmpty()) TranscriptResult.NothingHeard else TranscriptResult.Heard(text))
            }

            override fun onError(error: Int) {
                val noSpeech =
                    error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                finish(if (noSpeech) TranscriptResult.NothingHeard else TranscriptResult.Failed(error))
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() = Unit

            override fun onPartialResults(partialResults: Bundle?) = Unit

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }

        private companion object {
            const val MAX_LISTEN_MS = 30_000L
        }
    }

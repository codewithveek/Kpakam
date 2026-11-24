package app.kpakam.platform.speech

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import app.kpakam.core.audio.Loudness
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** The result of on-device speech recognition. */
sealed interface TranscriptResult {
    /** [confidence] is the recogniser's score for [text] (0–1), or `null` when it reported none. */
    data class Heard(val text: String, val confidence: Double?) : TranscriptResult

    data object NothingHeard : TranscriptResult

    data class Failed(val code: Int) : TranscriptResult
}

/** Turns English speech into text with the on-device recogniser (SPEECH_DEVICE). */
interface SpeechTranscriber {
    /**
     * Listens for up to 30 seconds and returns the transcript. With a [shared] capture (Android 13+),
     * the recogniser reads the app's own microphone audio, so the same audio is also kept as a clip.
     * [onPartial] receives the words heard so far, live, while the trader speaks.
     */
    suspend fun listen(shared: SharedCapture? = null, onPartial: (String) -> Unit = {}): TranscriptResult

    /** Stops listening early; [listen] then returns what it heard. */
    fun stop()
}

/** SpeechRecognizer adapter. Must run on the main thread, which [listen] ensures. */
@Singleton
class AndroidSpeechTranscriber
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val micLevel: MicLevel,
    ) : SpeechTranscriber {
        private val handler = Handler(Looper.getMainLooper())
        private var recognizer: SpeechRecognizer? = null

        override suspend fun listen(shared: SharedCapture?, onPartial: (String) -> Unit): TranscriptResult =
            withContext(Dispatchers.Main) {
                val created = create()
                recognizer = created
                try {
                    suspendCancellableCoroutine { continuation ->
                        // With a shared capture, the capture reports the loudness from the same audio.
                        val level: ((Float) -> Unit)? = if (shared == null) micLevel::report else null
                        created.setRecognitionListener(Listener(onPartial, level) { continuation.resume(it) })
                        created.startListening(intentFor(shared))
                        handler.postDelayed({ created.stopListening() }, MAX_LISTEN_MS)
                        continuation.invokeOnCancellation { handler.post { created.cancel() } }
                    }
                } finally {
                    handler.removeCallbacksAndMessages(null)
                    created.destroy()
                    recognizer = null
                    micLevel.reset()
                }
            }

        override fun stop() {
            handler.post { recognizer?.stopListening() }
        }

        private fun intentFor(shared: SharedCapture?): Intent {
            val intent = LocalSpeechChecker.englishIntent()
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            if (shared is PipedCapture && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, shared.recognizerAudio)
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, PipedCapture.SAMPLE_RATE)
            }
            return intent
        }

        private fun create(): SpeechRecognizer =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }

        private class Listener(
            private val onPartial: (String) -> Unit,
            private val onLevel: ((Float) -> Unit)?,
            private val done: (TranscriptResult) -> Unit,
        ) : RecognitionListener {
            private var finished = false

            private fun finish(result: TranscriptResult) {
                if (!finished) {
                    finished = true
                    done(result)
                }
            }

            override fun onResults(results: Bundle) {
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                val score = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.firstOrNull()
                val confidence = score?.toDouble()?.takeIf { it in 0.0..1.0 }
                finish(
                    if (text.isNullOrEmpty()) {
                        TranscriptResult.NothingHeard
                    } else {
                        TranscriptResult.Heard(
                            text,
                            confidence,
                        )
                    },
                )
            }

            override fun onError(error: Int) {
                val noSpeech =
                    error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                finish(if (noSpeech) TranscriptResult.NothingHeard else TranscriptResult.Failed(error))
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) {
                onLevel?.invoke(Loudness.ofRecognizerDb(rmsdB))
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                if (finished) return
                val words =
                    partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                if (!words.isNullOrEmpty()) onPartial(words)
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }

        private companion object {
            const val MAX_LISTEN_MS = 30_000L
        }
    }

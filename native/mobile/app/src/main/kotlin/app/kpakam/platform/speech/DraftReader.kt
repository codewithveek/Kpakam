package app.kpakam.platform.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Reads drafts aloud on request (F8). */
interface DraftReader {
    fun speak(text: String)

    fun stop()
}

/** TextToSpeech adapter. Prefers Nigerian English and falls back to the phone's English voice. */
@Singleton
class AndroidDraftReader
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : DraftReader {
        private var engine: TextToSpeech? = null
        private var ready = false
        private var pending: String? = null

        override fun speak(text: String) {
            val tts = engine ?: create()
            if (ready) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID) else pending = text
        }

        override fun stop() {
            engine?.stop()
        }

        private fun create(): TextToSpeech {
            lateinit var tts: TextToSpeech
            tts =
                TextToSpeech(context) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        val nigerian = Locale.forLanguageTag("en-NG")
                        val result = tts.setLanguage(nigerian)
                        if (result < TextToSpeech.LANG_AVAILABLE) tts.setLanguage(Locale.ENGLISH)
                        ready = true
                        pending?.let { tts.speak(it, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID) }
                        pending = null
                    }
                }
            engine = tts
            return tts
        }

        private companion object {
            const val UTTERANCE_ID = "kpakam-drafts"
        }
    }

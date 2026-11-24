package app.kpakam.core.language

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer
import java.util.Locale

/** The Pidgin/Yoruba marker list (`language-lexicon.json`). Map order is kept. */
@Serializable
public data class Lexicon(
    val version: String,
    val markers: Map<String, Double>,
    val phrases: Map<String, Double>,
) {
    public companion object {
        /** Parses `language-lexicon.json`. Throws [kotlinx.serialization.SerializationException] on bad JSON. */
        public fun parse(text: String): Lexicon = Json.decodeFromString(serializer(), text)
    }
}

/** Computes the Pidgin/Yoruba score from `routing.md`, step for step. */
public class LanguageScorer(private val lexicon: Lexicon) {
    /** Returns a score from 0 to 1, rounded to 2 decimals. Never throws. */
    public fun score(text: String): Double {
        val lower = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFC)
        val diacritics = lower.codePoints().filter { it in DIACRITICS }.count()
        val tokens = lower.split(TOKEN_SEPARATOR).filter { it.isNotEmpty() }
        val spaced = " ${tokens.joinToString(" ")} "
        var hits = 0.0
        for (token in tokens) hits += lexicon.markers[token] ?: 0.0
        for ((phrase, weight) in lexicon.phrases) {
            if (spaced.contains(" $phrase ")) hits += weight
        }
        val raw = (hits + HALF * diacritics) / maxOf(MIN_TOKENS, tokens.size) * 2
        return Math.round(minOf(1.0, raw) * PERCENT) / PERCENT
    }

    private companion object {
        const val HALF = 0.5
        const val MIN_TOKENS = 3
        const val PERCENT = 100.0
        val TOKEN_SEPARATOR = Regex("[^\\p{L}']+")
        val DIACRITICS: Set<Int> = "ẹọṣàáèéìíòóùúń".codePoints().toArray().toSet()
    }
}

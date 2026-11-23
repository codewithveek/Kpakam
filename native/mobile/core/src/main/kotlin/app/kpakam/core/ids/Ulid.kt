package app.kpakam.core.ids

import java.security.SecureRandom
import java.util.Random

/**
 * A ULID: 26 Crockford base-32 characters, sortable by creation time and created
 * offline without collisions. Used for journal, customer, job, and event ids.
 */
@JvmInline
public value class Ulid private constructor(public val value: String) {
    override fun toString(): String = value

    public companion object {
        private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        private const val TIME_CHARS = 10
        private const val RANDOM_CHARS = 16
        private const val BITS_PER_CHAR = 5
        private const val CHAR_MASK = 0x1F
        private const val MAX_TIME = (1L shl 48) - 1
        private val PATTERN = Regex("^[0-9A-HJKMNP-TV-Z]{26}$")
        private val defaultRandom: Random by lazy { SecureRandom() }

        /**
         * Returns the smallest ULID for [timeMillis]. ULIDs sort by time, so
         * `id >= lowerBound(t)` selects records created at or after `t`.
         */
        public fun lowerBound(timeMillis: Long): Ulid = Ulid(generate(timeMillis, ZeroRandom).value)

        /** Returns a [Ulid] when [text] matches the contract pattern, else `null`. */
        public fun parse(text: String): Ulid? = if (PATTERN.matches(text)) Ulid(text) else null

        /**
         * Creates a new ULID for [timeMillis] (Unix epoch ms) with 80 random bits.
         * Throws [IllegalArgumentException] when the time does not fit in 48 bits.
         */
        public fun generate(timeMillis: Long, random: Random = defaultRandom): Ulid {
            require(timeMillis in 0..MAX_TIME) { "timeMillis out of range" }
            val chars = CharArray(TIME_CHARS + RANDOM_CHARS)
            var time = timeMillis
            for (i in TIME_CHARS - 1 downTo 0) {
                chars[i] = ALPHABET[(time and CHAR_MASK.toLong()).toInt()]
                time = time shr BITS_PER_CHAR
            }
            for (i in TIME_CHARS until chars.size) {
                chars[i] = ALPHABET[random.nextInt(ALPHABET.length)]
            }
            return Ulid(String(chars))
        }
    }
}

/** A random source that always returns 0; used only to build [Ulid.lowerBound]. */
private object ZeroRandom : Random() {
    private const val serialVersionUID: Long = 1L

    override fun nextInt(bound: Int): Int = 0
}

/** A customer id. It is always a valid [Ulid]. */
@JvmInline
public value class CustomerId private constructor(public val value: String) {
    override fun toString(): String = value

    public companion object {
        /** Wraps a [Ulid] as a customer id. */
        public fun of(ulid: Ulid): CustomerId = CustomerId(ulid.value)

        /** Returns a [CustomerId] when [text] is a valid ULID, else `null`. */
        public fun parse(text: String): CustomerId? = Ulid.parse(text)?.let { CustomerId(it.value) }
    }
}

/** Creates new ids. Injected so tests can make ids deterministic. */
public fun interface IdGenerator {
    /** Returns a new, unique [Ulid]. */
    public fun next(): Ulid
}

/** An [IdGenerator] that uses the system clock and a secure random source. */
public object SystemIdGenerator : IdGenerator {
    override fun next(): Ulid = Ulid.generate(System.currentTimeMillis())
}

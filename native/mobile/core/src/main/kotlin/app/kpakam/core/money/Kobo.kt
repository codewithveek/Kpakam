package app.kpakam.core.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * An amount of money in integer kobo (1 naira = 100 kobo).
 *
 * All stored money is [Kobo]. A plain [Long] cannot be passed where money is expected.
 */
@JvmInline
public value class Kobo(public val value: Long) : Comparable<Kobo> {
    /** `true` when the amount is exactly zero. */
    public val isZero: Boolean get() = value == 0L

    public operator fun plus(other: Kobo): Kobo = Kobo(Math.addExact(value, other.value))

    public operator fun minus(other: Kobo): Kobo = Kobo(Math.subtractExact(value, other.value))

    public operator fun unaryMinus(): Kobo = Kobo(Math.negateExact(value))

    override fun compareTo(other: Kobo): Int = value.compareTo(other.value)

    public companion object {
        /** Zero kobo. */
        public val ZERO: Kobo = Kobo(0)

        private val HUNDRED = BigDecimal(100)

        /**
         * Converts naira from a model draft to kobo, exactly as `ledger.md` says.
         *
         * Takes the shortest round-trip decimal form of [naira] ([BigDecimal.valueOf]),
         * multiplies by 100, and rounds half up. `1.005` gives `101`, `12.345` gives `1235`.
         * Throws [ArithmeticException] only for values far outside the schema range.
         */
        public fun fromNaira(naira: Double): Kobo {
            require(naira.isFinite()) { "naira must be finite" }
            val kobo = BigDecimal.valueOf(naira).multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP)
            return Kobo(kobo.longValueExact())
        }
    }
}

/** Sums a sequence of amounts. Overflow throws, because it is a bug. */
public fun Iterable<Kobo>.sumKobo(): Kobo = fold(Kobo.ZERO) { acc, k -> acc + k }

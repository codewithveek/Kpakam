package app.kpakam.core.common

/**
 * The result of an operation that can fail in an expected way.
 *
 * Expected failures (a missing customer, invalid model output) are values, not
 * exceptions. Exceptions are reserved for bugs.
 */
public sealed interface Outcome<out T, out E> {
    /** The operation succeeded with [value]. */
    public data class Ok<out T>(val value: T) : Outcome<T, Nothing>

    /** The operation failed with [error]. */
    public data class Err<out E>(val error: E) : Outcome<Nothing, E>
}

/** Returns the success value, or `null` for an [Outcome.Err]. */
public fun <T, E> Outcome<T, E>.getOrNull(): T? = (this as? Outcome.Ok)?.value

/** Returns the error, or `null` for an [Outcome.Ok]. */
public fun <T, E> Outcome<T, E>.errorOrNull(): E? = (this as? Outcome.Err)?.error

/** Transforms the success value and keeps the error unchanged. */
public inline fun <T, E, R> Outcome<T, E>.map(transform: (T) -> R): Outcome<R, E> =
    when (this) {
        is Outcome.Ok -> Outcome.Ok(transform(value))
        is Outcome.Err -> this
    }

/** Chains another fallible step after a success. */
public inline fun <T, E, R> Outcome<T, E>.flatMap(transform: (T) -> Outcome<R, E>): Outcome<R, E> =
    when (this) {
        is Outcome.Ok -> transform(value)
        is Outcome.Err -> this
    }

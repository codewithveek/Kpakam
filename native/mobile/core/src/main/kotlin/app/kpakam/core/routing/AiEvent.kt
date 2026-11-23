package app.kpakam.core.routing

import app.kpakam.core.ids.Ulid

/** Which model served an AI event; [NONE] when no model ran (the job was queued). */
public enum class EventSource(public val wire: String) {
    ON_DEVICE("on_device"),
    IN_CLOUD("in_cloud"),
    NONE("none"),
}

/**
 * One `aiEvents` record. It never holds the trader's text, names, or amounts.
 *
 * [reason] is a [ReasonCode] name; [attempts] counts model calls (0–5).
 */
public data class AiEvent(
    val id: Ulid,
    val task: AiTask,
    val target: RouteTarget,
    val reason: ReasonCode,
    val source: EventSource,
    val latencyMs: Long,
    val valid: Boolean,
    val attempts: Int,
    val escalated: Boolean,
    val queuedMs: Long?,
    val promptVersion: String?,
) {
    init {
        require(attempts in 0..MAX_ATTEMPTS) { "attempts must be 0..$MAX_ATTEMPTS" }
        require(latencyMs >= 0) { "latencyMs must not be negative" }
    }

    public companion object {
        /** The highest attempt count the contract allows. */
        public const val MAX_ATTEMPTS: Int = 5

        /** The stored schema version. */
        public const val SCHEMA_VERSION: Int = 1
    }
}

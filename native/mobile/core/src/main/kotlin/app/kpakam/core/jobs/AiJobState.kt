package app.kpakam.core.jobs

/** State of a pending AI job. */
public enum class AiJobState {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

/** Thrown for a transition the state machine does not allow. It always means a bug. */
public class IllegalJobTransitionException(
    public val from: AiJobState,
    public val to: AiJobState,
) : IllegalStateException("AI job cannot go from $from to $to")

/**
 * The AI job state machine.
 *
 * Allowed: `QUEUED → RUNNING`, `RUNNING → SUCCEEDED | FAILED | CANCELLED`,
 * `RUNNING → QUEUED` (an interrupted job), and `FAILED → QUEUED` (a trader retry).
 * Every other transition throws [IllegalJobTransitionException].
 */
public object AiJobStateMachine {
    private val allowed: Map<AiJobState, Set<AiJobState>> =
        mapOf(
            AiJobState.QUEUED to setOf(AiJobState.RUNNING),
            AiJobState.RUNNING to
                setOf(
                    AiJobState.SUCCEEDED,
                    AiJobState.FAILED,
                    AiJobState.CANCELLED,
                    AiJobState.QUEUED,
                ),
            AiJobState.FAILED to setOf(AiJobState.QUEUED),
            AiJobState.SUCCEEDED to emptySet(),
            AiJobState.CANCELLED to emptySet(),
        )

    /** Returns `true` when [from] → [to] is allowed. */
    public fun canTransition(from: AiJobState, to: AiJobState): Boolean = to in allowed.getValue(from)

    /** Returns [to] when the transition is allowed; throws [IllegalJobTransitionException] otherwise. */
    public fun transition(from: AiJobState, to: AiJobState): AiJobState {
        if (!canTransition(from, to)) throw IllegalJobTransitionException(from, to)
        return to
    }
}

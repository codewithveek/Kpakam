package app.kpakam.core.jobs

import app.kpakam.core.jobs.AiJobState.CANCELLED
import app.kpakam.core.jobs.AiJobState.FAILED
import app.kpakam.core.jobs.AiJobState.QUEUED
import app.kpakam.core.jobs.AiJobState.RUNNING
import app.kpakam.core.jobs.AiJobState.SUCCEEDED
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertThrows

class AiJobStateMachineTest {
    private val allowed =
        setOf(
            QUEUED to RUNNING,
            RUNNING to SUCCEEDED,
            RUNNING to FAILED,
            RUNNING to CANCELLED,
            RUNNING to QUEUED,
            FAILED to QUEUED,
        )

    @TestFactory
    fun `every pair of states is allowed or throws`(): List<DynamicTest> =
        AiJobState.entries.flatMap { from -> AiJobState.entries.map { to -> from to to } }.map { (from, to) ->
            DynamicTest.dynamicTest("$from -> $to") {
                if ((from to to) in allowed) {
                    assertEquals(to, AiJobStateMachine.transition(from, to))
                } else {
                    val error = assertThrows<IllegalJobTransitionException> { AiJobStateMachine.transition(from, to) }
                    assertEquals(from, error.from)
                    assertEquals(to, error.to)
                }
            }
        }
}

package app.kpakam.core.routing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EscalationTest {
    @Test
    fun `first invalid device output retries on device`() {
        assertEquals(
            EscalationStep.RetryOnDevice,
            Escalation.afterInvalidDeviceOutput(1, online = false, cloudEnabled = false),
        )
    }

    @Test
    fun `second invalid output escalates to cloud when reachable`() {
        assertEquals(EscalationStep.Cloud, Escalation.afterInvalidDeviceOutput(2, online = true, cloudEnabled = true))
    }

    @Test
    fun `second invalid output queues offline`() {
        assertEquals(
            EscalationStep.Queue(QueueReason.OFFLINE),
            Escalation.afterInvalidDeviceOutput(2, online = false, cloudEnabled = true),
        )
    }

    @Test
    fun `second invalid output queues when cloud is disabled`() {
        assertEquals(
            EscalationStep.Queue(QueueReason.CLOUD_DISABLED),
            Escalation.afterInvalidDeviceOutput(2, online = true, cloudEnabled = false),
        )
    }

    @Test
    fun `zero attempts is a bug`() {
        assertThrows<IllegalArgumentException> {
            Escalation.afterInvalidDeviceOutput(
                0,
                online = true,
                cloudEnabled = true,
            )
        }
    }
}

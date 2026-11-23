package app.kpakam.core.routing

/** The next step after a device run produced invalid output. */
public sealed interface EscalationStep {
    /** Retry once on the device with the validation errors in the prompt. */
    public data object RetryOnDevice : EscalationStep

    /** Send the job to the cloud ([ReasonCode.VALIDATION_ESCALATION]). */
    public data object Cloud : EscalationStep

    /** Queue the job; it is routed again when the queue drains. */
    public data class Queue(val queueReason: QueueReason) : EscalationStep
}

/** Escalation on bad device output (`routing.md`, not part of the pure router). */
public object Escalation {
    /** Device attempts allowed before escalation: the first run plus one retry. */
    public const val MAX_DEVICE_ATTEMPTS: Int = 2

    /**
     * Decides what to do after [deviceAttempts] device runs (1 or more) that all failed validation.
     * Throws [IllegalArgumentException] when [deviceAttempts] is below 1.
     */
    public fun afterInvalidDeviceOutput(deviceAttempts: Int, online: Boolean, cloudEnabled: Boolean): EscalationStep {
        require(deviceAttempts >= 1) { "deviceAttempts must be at least 1" }
        return when {
            deviceAttempts < MAX_DEVICE_ATTEMPTS -> EscalationStep.RetryOnDevice
            online && cloudEnabled -> EscalationStep.Cloud
            online -> EscalationStep.Queue(QueueReason.CLOUD_DISABLED)
            else -> EscalationStep.Queue(QueueReason.OFFLINE)
        }
    }
}

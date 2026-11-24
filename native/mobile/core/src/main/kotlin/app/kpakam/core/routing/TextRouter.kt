package app.kpakam.core.routing

/** Inputs of the text and image router (`routing.md`). */
public data class TextRouteSignals(
    val task: AiTask,
    val chars: Int,
    val languageScore: Double,
    val online: Boolean,
    val cloudEnabled: Boolean,
    val dataSaver: Boolean,
    val approved: Boolean,
    val deviceModel: ModelState,
    val battery: BatteryState?,
) {
    /** `online && cloudEnabled`. */
    val cloudReachable: Boolean get() = online && cloudEnabled

    /** Why the cloud cannot be used right now. */
    val cloudBlockedReason: QueueReason get() = if (online) QueueReason.CLOUD_DISABLED else QueueReason.OFFLINE
}

/** The text and image router, version `routing-v1`. Pure: same inputs, same decision. */
public object TextRouter {
    /**
     * Picks where a job runs. Checks run in the order of `routing.md`; the first match wins.
     * Never throws.
     */
    public fun route(signals: TextRouteSignals, config: RoutingConfig): RouteDecision =
        with(signals) {
            when {
                task == AiTask.PARSE_IMAGE -> largeJob(ReasonCode.IMAGE_INPUT)
                task == AiTask.INSIGHTS -> largeJob(ReasonCode.LONG_CONTEXT)
                deviceModel != ModelState.AVAILABLE -> cloudOrQueue(ReasonCode.DEVICE_UNAVAILABLE)
                languageScore > config.languageThreshold -> cloudOrLowDevice(ReasonCode.LANGUAGE)
                chars > config.maxDeviceChars -> cloudOrLowDevice(ReasonCode.TOO_LONG)
                isLowBattery(config) && cloudReachable -> RouteDecision.Cloud(ReasonCode.LOW_BATTERY)
                else -> RouteDecision.Device(ReasonCode.DEFAULT_DEVICE)
            }
        }

    private fun TextRouteSignals.largeJob(reason: ReasonCode): RouteDecision =
        when {
            !cloudReachable -> RouteDecision.Queued(reason, cloudBlockedReason)
            dataSaver && !approved -> RouteDecision.Queued(reason, QueueReason.DATA_SAVER)
            else -> RouteDecision.Cloud(reason)
        }

    private fun TextRouteSignals.cloudOrQueue(reason: ReasonCode): RouteDecision =
        if (cloudReachable) RouteDecision.Cloud(reason) else RouteDecision.Queued(reason, cloudBlockedReason)

    private fun TextRouteSignals.cloudOrLowDevice(reason: ReasonCode): RouteDecision =
        if (cloudReachable) RouteDecision.Cloud(reason) else RouteDecision.Device(reason, Confidence.LOW)

    private fun TextRouteSignals.isLowBattery(config: RoutingConfig): Boolean =
        battery != null && !battery.charging && battery.level < config.minBattery
}

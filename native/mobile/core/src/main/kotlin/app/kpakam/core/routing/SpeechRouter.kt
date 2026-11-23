package app.kpakam.core.routing

/** Inputs of the speech router (`routing.md`). */
public data class SpeechRouteSignals(
    val voiceLanguage: VoiceLanguage,
    val localSpeech: ModelState,
    val online: Boolean,
    val cloudEnabled: Boolean,
)

/** The speech router. English with a local recogniser stays on the device. */
public object SpeechRouter {
    /**
     * Picks where a voice clip is handled: device ([ReasonCode.SPEECH_DEVICE], then the
     * transcript goes through [TextRouter]), cloud, or queue (the clip is saved). Never throws.
     */
    public fun route(signals: SpeechRouteSignals): RouteDecision =
        with(signals) {
            when {
                voiceLanguage == VoiceLanguage.EN && localSpeech == ModelState.AVAILABLE ->
                    RouteDecision.Device(ReasonCode.SPEECH_DEVICE)
                online && cloudEnabled -> RouteDecision.Cloud(ReasonCode.SPEECH_CLOUD)
                else ->
                    RouteDecision.Queued(
                        ReasonCode.SPEECH_CLOUD,
                        if (online) QueueReason.CLOUD_DISABLED else QueueReason.OFFLINE,
                    )
            }
        }
}

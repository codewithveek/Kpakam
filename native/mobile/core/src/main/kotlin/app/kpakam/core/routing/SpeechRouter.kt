package app.kpakam.core.routing

/** Inputs of the speech router (`routing.md`, routing-v2). */
public data class SpeechRouteSignals(
    val voiceLanguage: VoiceLanguage,
    val localSpeech: ModelState,
    val online: Boolean,
    val cloudEnabled: Boolean,
    /** `true` after the mixed-speech check failed for an on-device transcript (auto mode). */
    val mixedHeard: Boolean = false,
)

/** The speech router. English-capable modes with a local recogniser listen on the phone. */
public object SpeechRouter {
    /**
     * Picks where a voice entry is handled: device ([ReasonCode.SPEECH_DEVICE]; the transcript
     * then goes through [TextRouter]), cloud, or queue (the clip is saved). Never throws.
     */
    public fun route(signals: SpeechRouteSignals): RouteDecision =
        with(signals) {
            val listensLocally = voiceLanguage != VoiceLanguage.PCM_YO && localSpeech == ModelState.AVAILABLE
            when {
                mixedHeard -> cloudOrQueue(ReasonCode.SPEECH_MIXED)
                listensLocally -> RouteDecision.Device(ReasonCode.SPEECH_DEVICE)
                else -> cloudOrQueue(ReasonCode.SPEECH_CLOUD)
            }
        }

    private fun SpeechRouteSignals.cloudOrQueue(reason: ReasonCode): RouteDecision =
        when {
            online && cloudEnabled -> RouteDecision.Cloud(reason)
            online -> RouteDecision.Queued(reason, QueueReason.CLOUD_DISABLED)
            else -> RouteDecision.Queued(reason, QueueReason.OFFLINE)
        }
}

/** The mixed-speech check (auto mode only): can the English recogniser's words be trusted? */
public object MixedSpeech {
    /** Below this recogniser confidence the transcript is not trusted. Exactly 0.5 is trusted. */
    public const val MIN_SPEECH_CONFIDENCE: Double = 0.5

    /**
     * Returns `true` when the transcript is empty after trimming, when [languageScore] of the
     * transcript is above [threshold] (Pidgin or Yoruba heard), or when the recogniser reported a
     * [confidence] below [MIN_SPEECH_CONFIDENCE]. A `null` confidence means none was reported.
     */
    public fun isMixedSpeech(
        transcript: String,
        confidence: Double?,
        languageScore: Double,
        threshold: Double,
    ): Boolean =
        transcript.isBlank() ||
            languageScore > threshold ||
            (confidence != null && confidence < MIN_SPEECH_CONFIDENCE)
}

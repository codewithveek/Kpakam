package app.kpakam.core.routing

/** What an AI job does. [wire] is the value stored in `aiEvents.task`. */
public enum class AiTask(public val wire: String) {
    PARSE_TEXT("parse_text"),
    PARSE_IMAGE("parse_image"),
    PARSE_AUDIO("parse_audio"),
    SUMMARY("summary"),
    INSIGHTS("insights"),
}

/** State of an on-device model (Gemini Nano or a speech recogniser). */
public enum class ModelState(public val wire: String) {
    AVAILABLE("available"),
    DOWNLOADABLE("downloadable"),
    DOWNLOADING("downloading"),
    UNAVAILABLE("unavailable"),
    ;

    public companion object {
        /** Returns the state for a contract value, or `null` when unknown. */
        public fun fromWire(wire: String): ModelState? = entries.firstOrNull { it.wire == wire }
    }
}

/** Battery reading. [level] is 0–1. */
public data class BatteryState(val level: Double, val charging: Boolean)

/** The trader's voice language setting. */
public enum class VoiceLanguage(public val wire: String) {
    EN("en"),
    PCM_YO("pcm_yo"),
    ;

    public companion object {
        /** Returns the language for a stored value, or `null` when unknown. */
        public fun fromWire(wire: String): VoiceLanguage? = entries.firstOrNull { it.wire == wire }
    }
}

/** Where a job runs. */
public enum class RouteTarget(public val wire: String) {
    DEVICE("device"),
    CLOUD("cloud"),
    QUEUE("queue"),
}

/** Why the router chose a target. Labels for the UI live in the app's string resources. */
public enum class ReasonCode {
    IMAGE_INPUT,
    LONG_CONTEXT,
    DEVICE_UNAVAILABLE,
    LANGUAGE,
    TOO_LONG,
    LOW_BATTERY,
    DEFAULT_DEVICE,
    VALIDATION_ESCALATION,

    /** The safety net: reading signals or routing threw, so one `PREFER_ON_DEVICE` call ran. */
    ROUTER_FALLBACK,
    SPEECH_DEVICE,
    SPEECH_CLOUD,
}

/** Why a job waits in the queue. [RATE_LIMITED] is set by the job queue, never by the router. */
public enum class QueueReason {
    OFFLINE,
    DATA_SAVER,
    CLOUD_DISABLED,
    RATE_LIMITED,
}

/** How much to trust a device result. */
public enum class Confidence(public val wire: String) {
    NORMAL("normal"),
    LOW("low"),
}

/** The router's output: `{ target, reason, confidence, queueReason }`. */
public sealed interface RouteDecision {
    public val target: RouteTarget
    public val reason: ReasonCode
    public val confidence: Confidence
    public val queueReason: QueueReason?

    /** Run on the device model. */
    public data class Device(
        override val reason: ReasonCode,
        override val confidence: Confidence = Confidence.NORMAL,
    ) : RouteDecision {
        override val target: RouteTarget get() = RouteTarget.DEVICE
        override val queueReason: QueueReason? get() = null
    }

    /** Run on the cloud model. */
    public data class Cloud(override val reason: ReasonCode) : RouteDecision {
        override val target: RouteTarget get() = RouteTarget.CLOUD
        override val confidence: Confidence get() = Confidence.NORMAL
        override val queueReason: QueueReason? get() = null
    }

    /** Save the job and run it later. */
    public data class Queued(
        override val reason: ReasonCode,
        override val queueReason: QueueReason,
    ) : RouteDecision {
        override val target: RouteTarget get() = RouteTarget.QUEUE
        override val confidence: Confidence get() = Confidence.NORMAL
    }
}

/** Router thresholds from Remote Config. */
public data class RoutingConfig(
    val maxDeviceChars: Int,
    val languageThreshold: Double,
    val minBattery: Double,
)

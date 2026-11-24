package app.kpakam.core.config

import app.kpakam.core.routing.RoutingConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Remote Config keys shared with the PWA (`remote-config.defaults.json`). */
public object ConfigKeys {
    public const val CLOUD_MODEL_NAME: String = "cloud_model_name"
    public const val ROUTE_MAX_DEVICE_CHARS: String = "route_max_device_chars"
    public const val ROUTE_LANGUAGE_THRESHOLD: String = "route_language_threshold"
    public const val ROUTE_MIN_BATTERY: String = "route_min_battery"
    public const val PROMPT_VERSION_PARSE: String = "prompt_version_parse"
    public const val INSIGHTS_ENABLED: String = "insights_enabled"
    public const val CLOUD_ENABLED: String = "cloud_enabled"
    public const val CLOUD_RATE_LIMIT_PER_MINUTE: String = "cloud_rate_limit_per_minute"

    /** Every key the app reads. */
    public val ALL: List<String> =
        listOf(
            CLOUD_MODEL_NAME,
            ROUTE_MAX_DEVICE_CHARS,
            ROUTE_LANGUAGE_THRESHOLD,
            ROUTE_MIN_BATTERY,
            PROMPT_VERSION_PARSE,
            INSIGHTS_ENABLED,
            CLOUD_ENABLED,
            CLOUD_RATE_LIMIT_PER_MINUTE,
        )
}

/** The app's view of Remote Config. Values always exist, because defaults are bundled. */
public data class AppConfig(
    val cloudModelName: String,
    val routeMaxDeviceChars: Int,
    val routeLanguageThreshold: Double,
    val routeMinBattery: Double,
    val promptVersionParse: String,
    val insightsEnabled: Boolean,
    val cloudEnabled: Boolean,
    val cloudRateLimitPerMinute: Int,
) {
    /** The three numbers the router reads. */
    val routing: RoutingConfig
        get() = RoutingConfig(routeMaxDeviceChars, routeLanguageThreshold, routeMinBattery)

    public companion object {
        /**
         * Builds a config from string values, as Remote Config returns them.
         * Throws [IllegalArgumentException] when a key is missing or malformed.
         */
        public fun fromValues(value: (String) -> String?): AppConfig {
            fun str(key: String) = requireNotNull(value(key)) { "Missing config key $key" }
            return AppConfig(
                cloudModelName = str(ConfigKeys.CLOUD_MODEL_NAME),
                routeMaxDeviceChars = str(ConfigKeys.ROUTE_MAX_DEVICE_CHARS).toDouble().toInt(),
                routeLanguageThreshold = str(ConfigKeys.ROUTE_LANGUAGE_THRESHOLD).toDouble(),
                routeMinBattery = str(ConfigKeys.ROUTE_MIN_BATTERY).toDouble(),
                promptVersionParse = str(ConfigKeys.PROMPT_VERSION_PARSE),
                insightsEnabled = str(ConfigKeys.INSIGHTS_ENABLED).toBooleanStrict(),
                cloudEnabled = str(ConfigKeys.CLOUD_ENABLED).toBooleanStrict(),
                cloudRateLimitPerMinute = str(ConfigKeys.CLOUD_RATE_LIMIT_PER_MINUTE).toDouble().toInt(),
            )
        }

        /** Builds a config from a JSON object such as `remote-config.defaults.json`. */
        public fun fromJson(json: JsonObject): AppConfig = fromValues { key -> (json[key] as? JsonPrimitive)?.content }

        /** Parses `remote-config.defaults.json` text. */
        public fun fromJsonText(text: String): AppConfig = fromJson(Json.parseToJsonElement(text).jsonObject)
    }
}

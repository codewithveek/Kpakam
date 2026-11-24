package app.kpakam.core.config

import app.kpakam.core.ContractFiles
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppConfigTest {
    @Test
    fun `bundled defaults parse and cover every key`() {
        val json = ContractFiles.obj("remote-config.defaults.json")
        val config = AppConfig.fromJson(json)
        assertEquals(json.keys, ConfigKeys.ALL.toSet())
        assertEquals(280, config.routing.maxDeviceChars)
        assertEquals(0.35, config.routing.languageThreshold)
        assertEquals(0.15, config.routing.minBattery)
        assertTrue(config.cloudEnabled)
        assertTrue(config.cloudModelName.startsWith("gemini-"))
        assertTrue(
            config.cloudModelName
                .removePrefix("gemini-")
                .first()
                .digitToInt() >= 3,
            "no Gemini 1.x/2.x models",
        )
    }

    @Test
    fun `string values from Remote Config parse`() {
        val values =
            mapOf(
                ConfigKeys.CLOUD_MODEL_NAME to "gemini-3.5-flash-lite",
                ConfigKeys.ROUTE_MAX_DEVICE_CHARS to "280",
                ConfigKeys.ROUTE_LANGUAGE_THRESHOLD to "0.5",
                ConfigKeys.ROUTE_MIN_BATTERY to "0.2",
                ConfigKeys.PROMPT_VERSION_PARSE to "parse-v1",
                ConfigKeys.INSIGHTS_ENABLED to "false",
                ConfigKeys.CLOUD_ENABLED to "true",
                ConfigKeys.CLOUD_RATE_LIMIT_PER_MINUTE to "10",
            )
        val config = AppConfig.fromValues(values::get)
        assertEquals(0.5, config.routeLanguageThreshold)
        assertEquals(false, config.insightsEnabled)
        assertEquals(json(), json())
    }

    private fun json() = ContractFiles.json("remote-config.defaults.json").jsonObject.size
}

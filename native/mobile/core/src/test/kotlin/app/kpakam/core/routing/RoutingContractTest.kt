package app.kpakam.core.routing

import app.kpakam.core.ContractFiles
import app.kpakam.core.config.AppConfig
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/** Runs every case in `routing-cases.json` against the Kotlin routers. */
class RoutingContractTest {
    private val cases = ContractFiles.obj("routing-cases.json")
    private val defaults = ContractFiles.obj("remote-config.defaults.json")

    @Test
    fun `contract version is routing-v1`() {
        assertEquals("routing-v1", cases.getValue("version").jsonPrimitive.content)
        assertTrue(cases.getValue("text").jsonArray.isNotEmpty())
        assertTrue(cases.getValue("speech").jsonArray.isNotEmpty())
    }

    @TestFactory
    fun `text router matches every contract case`(): List<DynamicTest> {
        val defaultSignals = cases.getValue("defaultSignals").jsonObject
        return cases.getValue("text").jsonArray.map { element ->
            val case = element.jsonObject
            DynamicTest.dynamicTest(case.name()) {
                val signals = textSignals(merge(defaultSignals, case.getValue("signals").jsonObject))
                val config = AppConfig.fromJson(merge(defaults, case["config"]?.jsonObject)).routing
                val decision = TextRouter.route(signals, config)
                val expected = case.getValue("expected").jsonObject
                assertEquals(expected.str("target"), decision.target.wire, "target")
                assertEquals(expected.str("reason"), decision.reason.name, "reason")
                assertEquals(expected.str("confidence"), decision.confidence.wire, "confidence")
                assertEquals(expected.strOrNull("queueReason"), decision.queueReason?.name, "queueReason")
            }
        }
    }

    @TestFactory
    fun `speech router matches every contract case`(): List<DynamicTest> =
        cases.getValue("speech").jsonArray.map { element ->
            val case = element.jsonObject
            DynamicTest.dynamicTest(case.name()) {
                val s = case.getValue("signals").jsonObject
                val signals =
                    SpeechRouteSignals(
                        voiceLanguage = requireNotNull(VoiceLanguage.fromWire(s.str("voiceLanguage"))),
                        localSpeech = requireNotNull(ModelState.fromWire(s.str("localSpeech"))),
                        online = s.getValue("online").jsonPrimitive.boolean,
                        cloudEnabled = s.getValue("cloudEnabled").jsonPrimitive.boolean,
                    )
                val decision = SpeechRouter.route(signals)
                val expected = case.getValue("expected").jsonObject
                assertEquals(expected.str("target"), decision.target.wire, "target")
                assertEquals(expected.str("reason"), decision.reason.name, "reason")
                assertEquals(expected.strOrNull("queueReason"), decision.queueReason?.name, "queueReason")
            }
        }

    private fun textSignals(s: JsonObject) =
        TextRouteSignals(
            task = AiTask.entries.first { it.wire == s.str("task") },
            chars = s.getValue("chars").jsonPrimitive.int,
            languageScore = s.getValue("languageScore").jsonPrimitive.double,
            online = s.getValue("online").jsonPrimitive.boolean,
            cloudEnabled = s.getValue("cloudEnabled").jsonPrimitive.boolean,
            dataSaver = s.getValue("dataSaver").jsonPrimitive.boolean,
            approved = s.getValue("approved").jsonPrimitive.boolean,
            deviceModel = requireNotNull(ModelState.fromWire(s.str("deviceModel"))),
            battery = battery(s.getValue("battery")),
        )

    private fun battery(element: JsonElement): BatteryState? =
        if (element is JsonNull) {
            null
        } else {
            BatteryState(
                level =
                    element.jsonObject
                        .getValue("level")
                        .jsonPrimitive.double,
                charging =
                    element.jsonObject
                        .getValue("charging")
                        .jsonPrimitive.boolean,
            )
        }

    private fun merge(base: JsonObject, overlay: JsonObject?): JsonObject = JsonObject(base + (overlay ?: emptyMap()))

    private fun JsonObject.name() = str("name")

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

    private fun JsonObject.strOrNull(key: String): String? =
        get(
            key,
        )?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
}

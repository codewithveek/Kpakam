package app.kpakam.core.ledger

import app.kpakam.core.ContractFiles
import app.kpakam.core.TestFixtures
import app.kpakam.core.common.Outcome
import app.kpakam.core.draft.Draft
import app.kpakam.core.ids.CustomerId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/** Runs every case in `journal-cases.json` against [JournalBuilder]. */
class JournalContractTest {
    private val file = ContractFiles.obj("journal-cases.json")
    private val customerId = requireNotNull(CustomerId.parse(file.getValue("customerId").jsonPrimitive.content))

    @Test
    fun `contract version is ledger-v1`() {
        assertEquals("ledger-v1", file.getValue("version").jsonPrimitive.content)
    }

    @TestFactory
    fun `journal builder matches every contract case`(): List<DynamicTest> =
        file.getValue("cases").jsonArray.map { element ->
            val case = element.jsonObject
            DynamicTest.dynamicTest(case.getValue("name").jsonPrimitive.content) { runCase(case) }
        }

    private fun runCase(case: JsonObject) {
        val draft = Json.decodeFromJsonElement(Draft.serializer(), case.getValue("draft"))
        val customer = if (case.getValue("customer").jsonPrimitive.boolean) customerId else null
        val request =
            JournalRequest(
                draft = draft,
                customerId = customer,
                id = TestFixtures.ulid(),
                idempotencyKey = "case-key",
                occurredAt = TestFixtures.instant,
                source = TestFixtures.deviceSource,
            )
        val expected = case.getValue("expected").jsonObject
        val result = JournalBuilder.build(request)
        val error = expected["error"]?.jsonPrimitive?.content
        if (error != null) {
            assertTrue(result is Outcome.Err, "expected error $error but got $result")
            assertEquals(error, (result as Outcome.Err).error.code)
            return
        }
        assertTrue(result is Outcome.Ok, "expected a journal but got $result")
        val journal = (result as Outcome.Ok).value
        assertEquals(expected.getValue("kind").jsonPrimitive.content, journal.kind.wire, "kind")
        assertEquals(expected.getValue("description").jsonPrimitive.content, journal.description, "description")
        assertEquals(expectedLines(expected), journal.lines.map { it.account.key to it.amount.value }, "lines")
        assertEquals(customer, journal.customerId, "customerId is kept when resolved")
        assertEquals(emptyList<String>(), TestFixtures.journalSchema.validate(JournalJson.toJson(journal)))
    }

    private fun expectedLines(expected: JsonObject): List<Pair<String, Long>> =
        expected.getValue("lines").jsonArray.map { line ->
            val pair = line.jsonArray
            val account = pair[0].jsonPrimitive.content
            val key = if (account == "receivable") "receivable:${customerId.value}" else account
            key to pair[1].jsonPrimitive.long
        }
}

package app.kpakam.core.golden

import app.kpakam.core.ContractFiles
import app.kpakam.core.TestFixtures
import app.kpakam.core.common.Outcome
import app.kpakam.core.draft.DraftValidation
import app.kpakam.core.ids.CustomerId
import app.kpakam.core.ledger.JournalBuilder
import app.kpakam.core.ledger.JournalRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Every golden line's expected output passes the Kotlin validator (schema and domain rules)
 * and builds balanced journals, so the golden sets and the Android ledger agree.
 */
class GoldenSetContractTest {
    private val customer = CustomerId.of(TestFixtures.ulid())

    @TestFactory
    fun `every golden expected output is valid and builds journals`(): List<DynamicTest> =
        listOf("golden/en.jsonl", "golden/pcm.jsonl").flatMap { file ->
            ContractFiles.text(file).lines().filter { it.isNotBlank() }.map { line ->
                val entry = Json.parseToJsonElement(line).jsonObject
                DynamicTest.dynamicTest(entry.getValue("id").jsonPrimitive.content) { check(entry) }
            }
        }

    private fun check(entry: kotlinx.serialization.json.JsonObject) {
        assertTrue(
            entry
                .getValue("text")
                .jsonPrimitive.content.length <= MAX_NOTE,
        )
        val validation = TestFixtures.draftValidator.validate(entry.getValue("expected").toString())
        assertTrue(validation is DraftValidation.Valid, validation.toString())
        (validation as DraftValidation.Valid).response.drafts.forEach { draft ->
            val customerId = customer.takeUnless { draft.customerName.isNullOrBlank() }
            val request =
                JournalRequest(
                    draft,
                    customerId,
                    TestFixtures.ulid(),
                    "golden",
                    TestFixtures.instant,
                    TestFixtures.deviceSource,
                )
            val built = JournalBuilder.build(request)
            assertTrue(built is Outcome.Ok, built.toString())
            assertEquals(0L, (built as Outcome.Ok).value.lines.sumOf { it.amount.value })
        }
    }

    private companion object {
        const val MAX_NOTE = 280
    }
}

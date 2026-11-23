package app.kpakam.core.ledger

import app.kpakam.core.ContractFiles
import app.kpakam.core.TestFixtures
import app.kpakam.core.common.getOrNull
import app.kpakam.core.draft.Draft
import app.kpakam.core.ids.CustomerId
import app.kpakam.core.ids.Ulid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JournalDraftsTest {
    private val customer = CustomerId.of(TestFixtures.ulid())

    private fun build(draft: Draft, customerId: CustomerId?) =
        JournalBuilder
            .build(
                JournalRequest(
                    draft,
                    customerId,
                    TestFixtures.ulid(),
                    "k",
                    TestFixtures.instant,
                    TestFixtures.deviceSource,
                ),
            ).getOrNull()

    @Test
    fun `every valid contract journal rebuilds the same lines from its draft`() {
        val cases = ContractFiles.obj("journal-cases.json").getValue("cases").jsonArray
        cases.map { it.jsonObject }.filter { "error" !in it.getValue("expected").jsonObject }.forEach { case ->
            val draft = Json.decodeFromJsonElement(Draft.serializer(), case.getValue("draft"))
            val customerId = customer.takeIf { case.getValue("customer").jsonPrimitive.boolean }
            val journal = requireNotNull(build(draft, customerId))
            val again = requireNotNull(build(requireNotNull(JournalDrafts.toDraft(journal, "Ada")), customerId))
            assertEquals(journal.lines, again.lines, case.getValue("name").jsonPrimitive.content)
            assertEquals(journal.kind, again.kind)
        }
    }

    @Test
    fun `a reversal has no draft`() {
        val journal =
            requireNotNull(build(Draft(app.kpakam.core.draft.DraftKind.SALE, totalNaira = 5.0, confidence = 1.0), null))
        val reversal =
            requireNotNull(
                Reversal
                    .reverse(
                        ReversalRequest(
                            journal,
                            false,
                            TestFixtures.ulid(),
                            "r",
                            TestFixtures.instant,
                            TestFixtures.manualSource,
                        ),
                    ).getOrNull(),
            )
        assertNull(JournalDrafts.toDraft(reversal, null))
    }

    @Test
    fun `kobo converts back to naira exactly`() {
        assertEquals(
            12.35,
            JournalDrafts.toNaira(
                app.kpakam.core.money
                    .Kobo(1235),
            ),
        )
        assertEquals(
            1.01,
            JournalDrafts.toNaira(
                app.kpakam.core.money
                    .Kobo(101),
            ),
        )
    }

    @Test
    fun `ulid lower bound sorts before every id of that millisecond`() {
        val bound = Ulid.lowerBound(1_700_000_000_000)
        assertTrue(bound.value.endsWith("0000000000000000"))
        repeat(100) { assertTrue(Ulid.generate(1_700_000_000_000).value >= bound.value) }
        assertTrue(Ulid.generate(1_699_999_999_999).value < bound.value)
    }
}

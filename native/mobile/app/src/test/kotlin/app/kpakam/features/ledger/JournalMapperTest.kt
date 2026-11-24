package app.kpakam.features.ledger

import app.kpakam.core.common.getOrNull
import app.kpakam.core.draft.Draft
import app.kpakam.core.draft.DraftKind
import app.kpakam.core.ids.CustomerId
import app.kpakam.core.ids.Ulid
import app.kpakam.core.ledger.InferenceSource
import app.kpakam.core.ledger.JournalBuilder
import app.kpakam.core.ledger.JournalRequest
import app.kpakam.core.ledger.JournalSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class JournalMapperTest {
    private val journal =
        requireNotNull(
            JournalBuilder
                .build(
                    JournalRequest(
                        draft = Draft(DraftKind.CREDIT_SALE, "rice", 3.0, "bag", 45000.0, 35000.0, "Mama Bisi", 0.9),
                        customerId = CustomerId.parse("01JDX6M8Z9Q4W2E3R5T7Y9V1K0"),
                        id = Ulid.generate(1_764_000_000_000),
                        idempotencyKey = "req:0",
                        occurredAt = Instant.parse("2026-11-24T08:41:00Z"),
                        source = JournalSource(InferenceSource.ON_DEVICE, "DEFAULT_DEVICE", "parse-v1"),
                    ),
                ).getOrNull(),
        )

    @Test
    fun documentHasExactlyTheContractKeys() {
        val keys =
            setOf(
                "id",
                "idempotencyKey",
                "occurredAt",
                "day",
                "kind",
                "description",
                "customerId",
                "lines",
                "reverses",
                "source",
                "createdAt",
                "schemaVersion",
            )
        val map = JournalMapper.toMap(journal)
        assertEquals(keys, map.keys)
        assertEquals("2026-11-24T09:41:00+01:00", map["occurredAt"])
        assertEquals("2026-11-24", map["day"])
        assertEquals(
            listOf(3_500_000L, 1_000_000L, -4_500_000L),
            (map["lines"] as List<*>).map {
                (it as Map<*, *>)["amountKobo"]
            },
        )
    }

    @Test
    fun aStoredJournalReadsBackUnchanged() {
        assertEquals(journal, JournalMapper.fromMap(JournalMapper.toMap(journal)))
    }

    @Test
    fun aDocumentThatBreaksTheContractIsSkipped() {
        assertNull(JournalMapper.fromMap(JournalMapper.toMap(journal) + ("kind" to "gift")))
        assertNull(JournalMapper.fromMap(JournalMapper.toMap(journal) - "lines"))
    }
}

package app.kpakam.shared.ui

import app.kpakam.core.common.getOrNull
import app.kpakam.core.draft.Draft
import app.kpakam.core.draft.DraftKind
import app.kpakam.core.ids.CustomerId
import app.kpakam.core.ids.Ulid
import app.kpakam.core.ledger.InferenceSource
import app.kpakam.core.ledger.Journal
import app.kpakam.core.ledger.JournalBuilder
import app.kpakam.core.ledger.JournalRequest
import app.kpakam.core.ledger.JournalSource
import app.kpakam.core.money.Kobo
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class EntryAmountsTest {
    private val customer = CustomerId.parse("01JD0000000000000000000000")!!

    private fun journal(draft: Draft, customerId: CustomerId? = null): Journal =
        JournalBuilder
            .build(
                JournalRequest(
                    draft,
                    customerId,
                    Ulid.parse("01JD0000000000000000000001")!!,
                    "key",
                    Instant.parse("2026-11-24T09:00:00Z"),
                    JournalSource(InferenceSource.MANUAL, "MANUAL", null),
                ),
            ).getOrNull()!!

    @Test
    fun `credit sale shows the full total and what is still owed`() {
        val sale =
            journal(
                Draft(
                    DraftKind.CREDIT_SALE,
                    totalNaira = 45_000.0,
                    paidNaira = 35_000.0,
                    customerName = "Bisi",
                    confidence = 1.0,
                ),
                customer,
            )
        assertEquals(Kobo.fromNaira(45_000.0), EntryAmounts.headline(sale))
        assertEquals(Kobo.fromNaira(10_000.0), EntryAmounts.creditOwed(sale))
        assertEquals(MoneyTone.Income, EntryAmounts.tone(sale, reversed = false))
    }

    @Test
    fun `expense is money out and undone entries are plain`() {
        val expense = journal(Draft(DraftKind.EXPENSE, totalNaira = 1_500.0, confidence = 1.0))
        assertEquals(Kobo.fromNaira(1_500.0), EntryAmounts.headline(expense))
        assertEquals(Kobo.ZERO, EntryAmounts.creditOwed(expense))
        assertEquals(MoneyTone.Expense, EntryAmounts.tone(expense, reversed = false))
        assertEquals(MoneyTone.Plain, EntryAmounts.tone(expense, reversed = true))
    }
}

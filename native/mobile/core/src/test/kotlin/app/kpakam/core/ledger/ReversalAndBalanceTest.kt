package app.kpakam.core.ledger

import app.kpakam.core.TestFixtures
import app.kpakam.core.common.Outcome
import app.kpakam.core.common.getOrNull
import app.kpakam.core.draft.Draft
import app.kpakam.core.draft.DraftKind
import app.kpakam.core.ids.CustomerId
import app.kpakam.core.ids.Ulid
import app.kpakam.core.money.Kobo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ReversalAndBalanceTest {
    private val customer = CustomerId.of(TestFixtures.ulid())

    private fun journal(draft: Draft, customerId: CustomerId? = customer): Journal =
        requireNotNull(
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
                ).getOrNull(),
        )

    private fun reverse(original: Journal, alreadyReversed: Boolean = false) =
        Reversal.reverse(
            ReversalRequest(
                original,
                alreadyReversed,
                TestFixtures.ulid(),
                "r",
                TestFixtures.instant,
                TestFixtures.manualSource,
            ),
        )

    private val creditSale = Draft(DraftKind.CREDIT_SALE, "rice", 3.0, "bag", 45000.0, 35000.0, "Mama Bisi", 0.9)
    private val repayment =
        Draft(DraftKind.REPAYMENT, totalNaira = 4000.0, customerName = "Mama Bisi", confidence = 0.9)

    @Test
    fun `reversal flips every line in order and links the original`() {
        val original = journal(creditSale)
        val reversal = requireNotNull(reverse(original).getOrNull())
        assertEquals(JournalKind.REVERSAL, reversal.kind)
        assertEquals(original.id, reversal.reverses)
        assertEquals(original.customerId, reversal.customerId)
        assertEquals("Reversal: 3 bag rice", reversal.description)
        assertEquals(original.lines.map { it.account to -it.amount }, reversal.lines.map { it.account to it.amount })
        assertEquals(emptyList<String>(), TestFixtures.journalSchema.validate(JournalJson.toJson(reversal)))
    }

    @Test
    fun `a reversed journal cannot be reversed again`() {
        assertEquals(Outcome.Err(LedgerError.AlreadyReversed), reverse(journal(creditSale), alreadyReversed = true))
    }

    @Test
    fun `a reversal can itself be reversed and restores the balance`() {
        val original = journal(creditSale)
        val reversal = requireNotNull(reverse(original).getOrNull())
        val again = requireNotNull(reverse(reversal).getOrNull())
        assertEquals(Kobo(1_000_000), Balances.customerBalance(listOf(original, reversal, again), customer))
    }

    @Test
    fun `reversal description is trimmed to 120 characters`() {
        val long =
            journal(
                Draft(
                    DraftKind.SALE,
                    item = "x".repeat(80),
                    quantity = 1.0,
                    unit = "y".repeat(30),
                    totalNaira = 5.0,
                    confidence = 1.0,
                ),
            )
        assertEquals(120, requireNotNull(reverse(long).getOrNull()).description.length)
    }

    @Test
    fun `customer balance sums receivable lines`() {
        val journals = listOf(journal(creditSale), journal(repayment))
        assertEquals(Kobo(600_000), Balances.customerBalance(journals, customer))
        assertEquals(mapOf(customer to Kobo(600_000)), Balances.allCustomerBalances(journals))
    }

    @Test
    fun `day totals derive sales, expenses, profit, and debt collected`() {
        val expense = journal(Draft(DraftKind.EXPENSE, item = "transport", totalNaira = 1500.0, confidence = 1.0), null)
        val pay = journal(repayment)
        val journals = listOf(journal(creditSale), expense, pay)
        val totals = Balances.dayTotals(journals) { id -> journals.firstOrNull { it.id == id } }
        assertEquals(DayTotals(Kobo(4_500_000), Kobo(150_000), Kobo(4_350_000), Kobo(400_000)), totals)
    }

    @Test
    fun `an undone repayment nets out of debt collected, and a redo counts again`() {
        val pay = journal(repayment)
        val undo = requireNotNull(reverse(pay).getOrNull())
        val redo = requireNotNull(reverse(undo).getOrNull())
        val all = listOf(pay, undo, redo)
        val lookup: (Ulid) -> Journal? = { id -> all.firstOrNull { it.id == id } }
        assertEquals(Kobo.ZERO, Balances.dayTotals(listOf(pay, undo), lookup).debtCollected)
        assertEquals(Kobo(400_000), Balances.dayTotals(all, lookup).debtCollected)
    }

    @Test
    fun `a reversed sale nets out of sales`() {
        val sale = journal(Draft(DraftKind.SALE, item = "bread", totalNaira = 1200.0, confidence = 1.0), null)
        val undo = requireNotNull(reverse(sale).getOrNull())
        val totals = Balances.dayTotals(listOf(sale, undo)) { null }
        assertEquals(DayTotals.EMPTY, totals)
    }

    @Test
    fun `root kind is unknown when the original is not loaded`() {
        val undo = requireNotNull(reverse(journal(repayment)).getOrNull())
        assertNull(Balances.rootKind(undo) { null })
    }
}

package app.kpakam.core.ledger

import app.kpakam.core.TestFixtures
import app.kpakam.core.common.Outcome
import app.kpakam.core.common.getOrNull
import app.kpakam.core.draft.Draft
import app.kpakam.core.draft.DraftKind
import app.kpakam.core.ids.CustomerId
import app.kpakam.core.money.Kobo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

class InvariantsTest {
    @Test
    fun `balanced lines pass`() {
        val lines = listOf(JournalLine(Account.Cash, Kobo(100)), JournalLine(Account.Sales, Kobo(-100)))
        assertEquals(Outcome.Ok(Unit), Invariants.check(lines))
    }

    @Test
    fun `unbalanced, zero, too few, and too many lines fail`() {
        val bad =
            listOf(
                listOf(JournalLine(Account.Cash, Kobo(100)), JournalLine(Account.Sales, Kobo(-99))),
                listOf(JournalLine(Account.Cash, Kobo(0)), JournalLine(Account.Sales, Kobo(0))),
                listOf(JournalLine(Account.Cash, Kobo(100))),
                List(11) { JournalLine(Account.Cash, Kobo(if (it == 10) -1000 else 100)) },
            )
        bad.forEach { assertTrue(Invariants.check(it) is Outcome.Err, "should fail: $it") }
    }

    @Test
    fun `account strings round-trip`() {
        val customer = CustomerId.of(TestFixtures.ulid())
        listOf(Account.Cash, Account.Sales, Account.Expenses, Account.Receivable(customer)).forEach {
            assertEquals(it, Account.parse(it.key))
        }
        assertEquals(null, Account.parse("receivable:not-a-ulid"))
        assertEquals(null, Account.parse("bank"))
    }

    @Test
    fun `every random draft builds a zero-sum journal or a known error`() {
        val random = Random(7)
        val customer = CustomerId.of(TestFixtures.ulid())
        repeat(2_000) {
            val total = random.nextInt(10_000_000) / 100.0 + 0.01
            val draft =
                Draft(
                    kind = DraftKind.entries[random.nextInt(DraftKind.entries.size)],
                    totalNaira = total,
                    paidNaira = if (random.nextBoolean()) random.nextDouble() * total * 1.2 else null,
                    customerName = "Ada",
                    confidence = 1.0,
                )
            val built =
                JournalBuilder.build(
                    JournalRequest(
                        draft,
                        customer.takeIf {
                            random.nextBoolean()
                        },
                        TestFixtures.ulid(),
                        "k",
                        TestFixtures.instant,
                        TestFixtures.deviceSource,
                    ),
                )
            val journal = built.getOrNull() ?: return@repeat
            assertEquals(0L, journal.lines.sumOf { it.amount.value })
        }
    }
}

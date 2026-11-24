package app.kpakam.core.ledger

import app.kpakam.core.ids.CustomerId
import app.kpakam.core.ids.Ulid
import app.kpakam.core.money.Kobo

/** The stored kind of a journal (`journal.schema.json`). */
public enum class JournalKind(public val wire: String, public val label: String) {
    SALE("sale", "Sale"),
    EXPENSE("expense", "Expense"),
    REPAYMENT("repayment", "Repayment"),
    CREDIT_SALE("credit_sale", "Credit sale"),
    REVERSAL("reversal", "Reversal"),
    ;

    public companion object {
        /** Returns the kind for a stored value, or `null` when unknown. */
        public fun fromWire(wire: String): JournalKind? = entries.firstOrNull { it.wire == wire }
    }
}

/** A ledger account. */
public sealed interface Account {
    /** The stored account string, e.g. `cash` or `receivable:01JD...`. */
    public val key: String

    /** Money in hand or in the bank. */
    public data object Cash : Account {
        override val key: String = "cash"
    }

    /** Revenue from goods sold. */
    public data object Sales : Account {
        override val key: String = "sales"
    }

    /** Money spent on stock, transport, rent, and so on. */
    public data object Expenses : Account {
        override val key: String = "expenses"
    }

    /** Money [customerId] owes the trader. */
    public data class Receivable(val customerId: CustomerId) : Account {
        override val key: String get() = "$PREFIX${customerId.value}"
    }

    public companion object {
        private const val PREFIX = "receivable:"

        /** Parses a stored account string, or returns `null` when it does not match the contract. */
        public fun parse(key: String): Account? =
            when {
                key == Cash.key -> Cash
                key == Sales.key -> Sales
                key == Expenses.key -> Expenses
                key.startsWith(PREFIX) -> CustomerId.parse(key.removePrefix(PREFIX))?.let(::Receivable)
                else -> null
            }
    }
}

/** One journal line. Debit is positive, credit is negative. */
public data class JournalLine(val account: Account, val amount: Kobo)

/** Where the numbers in a journal came from. */
public enum class InferenceSource(public val wire: String) {
    ON_DEVICE("on_device"),
    IN_CLOUD("in_cloud"),
    MANUAL("manual"),
    ;

    public companion object {
        /** Returns the source for a stored value, or `null` when unknown. */
        public fun fromWire(wire: String): InferenceSource? = entries.firstOrNull { it.wire == wire }
    }
}

/** The `source` object of a journal. */
public data class JournalSource(
    val inference: InferenceSource,
    val reason: String,
    val promptVersion: String?,
)

/** One immutable double-entry journal. Lines sum to zero. */
public data class Journal(
    val id: Ulid,
    val idempotencyKey: String,
    val occurredAt: String,
    val day: String,
    val kind: JournalKind,
    val description: String,
    val customerId: CustomerId?,
    val lines: List<JournalLine>,
    val reverses: Ulid?,
    val source: JournalSource,
) {
    /** The stored schema version. */
    val schemaVersion: Int get() = SCHEMA_VERSION

    public companion object {
        /** The only known journal schema version. */
        public const val SCHEMA_VERSION: Int = 1
    }
}

/** An expected ledger failure. [code] is the contract's error code. */
public sealed interface LedgerError {
    public val code: String

    /** A repayment, or a credit sale that is not fully paid, has no customer. */
    public data object CustomerRequired : LedgerError {
        override val code: String = "CUSTOMER_REQUIRED"
    }

    /** A credit sale's paid amount is more than its total. */
    public data object PaidExceedsTotal : LedgerError {
        override val code: String = "PAID_EXCEEDS_TOTAL"
    }

    /** The total rounds to 0 kobo, or the paid amount rounds below zero. */
    public data object AmountTooSmall : LedgerError {
        override val code: String = "AMOUNT_TOO_SMALL"
    }

    /** The journal already has a reversal. */
    public data object AlreadyReversed : LedgerError {
        override val code: String = "ALREADY_REVERSED"
    }

    /** The lines break an invariant. This means a bug upstream. */
    public data class InvariantViolated(val reason: String) : LedgerError {
        override val code: String = "INVARIANT_VIOLATED"
    }
}

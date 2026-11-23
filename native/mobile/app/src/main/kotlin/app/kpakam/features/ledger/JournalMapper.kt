package app.kpakam.features.ledger

import app.kpakam.core.ids.CustomerId
import app.kpakam.core.ids.Ulid
import app.kpakam.core.ledger.Account
import app.kpakam.core.ledger.InferenceSource
import app.kpakam.core.ledger.Journal
import app.kpakam.core.ledger.JournalFields
import app.kpakam.core.ledger.JournalKind
import app.kpakam.core.ledger.JournalLine
import app.kpakam.core.ledger.JournalSource
import app.kpakam.core.money.Kobo
import app.kpakam.core.time.LagosTime
import com.google.firebase.firestore.FieldValue

/** Converts journals to and from Firestore maps. The keys match the PWA exactly. */
object JournalMapper {
    /** The document for [journal]. `createdAt` is a server timestamp. */
    fun toMap(journal: Journal): Map<String, Any?> =
        mapOf(
            JournalFields.ID to journal.id.value,
            JournalFields.IDEMPOTENCY_KEY to journal.idempotencyKey,
            JournalFields.OCCURRED_AT to journal.occurredAt,
            JournalFields.DAY to journal.day,
            JournalFields.KIND to journal.kind.wire,
            JournalFields.DESCRIPTION to journal.description,
            JournalFields.CUSTOMER_ID to journal.customerId?.value,
            JournalFields.LINES to
                journal.lines.map {
                    mapOf(JournalFields.ACCOUNT to it.account.key, JournalFields.AMOUNT_KOBO to it.amount.value)
                },
            JournalFields.REVERSES to journal.reverses?.value,
            JournalFields.SOURCE to
                mapOf(
                    JournalFields.INFERENCE to journal.source.inference.wire,
                    JournalFields.REASON to journal.source.reason,
                    JournalFields.PROMPT_VERSION to journal.source.promptVersion,
                ),
            JournalFields.CREATED_AT to FieldValue.serverTimestamp(),
            JournalFields.SCHEMA_VERSION to Journal.SCHEMA_VERSION,
        )

    /** Reads a stored journal. Returns `null` for a document that does not match the contract. */
    fun fromMap(data: Map<String, Any?>): Journal? {
        val occurredAt = data[JournalFields.OCCURRED_AT] as? String ?: return null
        val source = data[JournalFields.SOURCE] as? Map<*, *> ?: return null
        return Journal(
            id = (data[JournalFields.ID] as? String)?.let(Ulid::parse) ?: return null,
            idempotencyKey = data[JournalFields.IDEMPOTENCY_KEY] as? String ?: return null,
            occurredAt = occurredAt,
            day = data[JournalFields.DAY] as? String ?: LagosTime.dayOfOccurredAt(occurredAt),
            kind = (data[JournalFields.KIND] as? String)?.let(JournalKind::fromWire) ?: return null,
            description = data[JournalFields.DESCRIPTION] as? String ?: "",
            customerId = (data[JournalFields.CUSTOMER_ID] as? String)?.let(CustomerId::parse),
            lines = lines(data[JournalFields.LINES]) ?: return null,
            reverses = (data[JournalFields.REVERSES] as? String)?.let(Ulid::parse),
            source =
                JournalSource(
                    inference =
                        (source[JournalFields.INFERENCE] as? String)?.let(InferenceSource::fromWire) ?: return null,
                    reason = source[JournalFields.REASON] as? String ?: "",
                    promptVersion = source[JournalFields.PROMPT_VERSION] as? String,
                ),
        )
    }

    private fun lines(value: Any?): List<JournalLine>? {
        val raw = value as? List<*> ?: return null
        return raw.map { item ->
            val line = item as? Map<*, *> ?: return null
            val account = (line[JournalFields.ACCOUNT] as? String)?.let(Account::parse) ?: return null
            val amount = (line[JournalFields.AMOUNT_KOBO] as? Number)?.toLong() ?: return null
            JournalLine(account, Kobo(amount))
        }
    }
}

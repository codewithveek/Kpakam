package app.kpakam.features.ledger

import android.util.Log
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
    private const val TAG = "JournalMapper"

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

    /** Reads a stored journal. Returns `null` (and logs) for a document that does not match the contract. */
    fun fromMap(data: Map<String, Any?>): Journal? =
        try {
            parse(data)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Skipping a journal that does not match the contract", e)
            null
        }

    private fun parse(data: Map<String, Any?>): Journal {
        val occurredAt = required<String>(data, JournalFields.OCCURRED_AT)
        val source = required<Map<*, *>>(data, JournalFields.SOURCE)
        return Journal(
            id = requireNotNull(Ulid.parse(required(data, JournalFields.ID))) { "bad id" },
            idempotencyKey = required(data, JournalFields.IDEMPOTENCY_KEY),
            occurredAt = occurredAt,
            day = data[JournalFields.DAY] as? String ?: LagosTime.dayOfOccurredAt(occurredAt),
            kind = requireNotNull(JournalKind.fromWire(required(data, JournalFields.KIND))) { "bad kind" },
            description = data[JournalFields.DESCRIPTION] as? String ?: "",
            customerId = (data[JournalFields.CUSTOMER_ID] as? String)?.let(CustomerId::parse),
            lines = requireNotNull(lines(data[JournalFields.LINES])) { "bad lines" },
            reverses = (data[JournalFields.REVERSES] as? String)?.let(Ulid::parse),
            source =
                JournalSource(
                    inference =
                        requireNotNull(
                            (source[JournalFields.INFERENCE] as? String)?.let(InferenceSource::fromWire),
                        ),
                    reason = source[JournalFields.REASON] as? String ?: "",
                    promptVersion = source[JournalFields.PROMPT_VERSION] as? String,
                ),
        )
    }

    private inline fun <reified T> required(data: Map<String, Any?>, key: String): T =
        requireNotNull(data[key] as? T) { "missing $key" }

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

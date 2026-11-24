package app.kpakam.core.ledger

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Stored field names of a journal document. Both apps use exactly these keys. */
public object JournalFields {
    public const val ID: String = "id"
    public const val IDEMPOTENCY_KEY: String = "idempotencyKey"
    public const val OCCURRED_AT: String = "occurredAt"
    public const val DAY: String = "day"
    public const val KIND: String = "kind"
    public const val DESCRIPTION: String = "description"
    public const val CUSTOMER_ID: String = "customerId"
    public const val LINES: String = "lines"
    public const val ACCOUNT: String = "account"
    public const val AMOUNT_KOBO: String = "amountKobo"
    public const val REVERSES: String = "reverses"
    public const val SOURCE: String = "source"
    public const val INFERENCE: String = "inference"
    public const val REASON: String = "reason"
    public const val PROMPT_VERSION: String = "promptVersion"
    public const val CREATED_AT: String = "createdAt"
    public const val SCHEMA_VERSION: String = "schemaVersion"
}

/** Converts a journal to the contract's JSON shape (without the server-set `createdAt`). */
public object JournalJson {
    /** Returns [journal] as a JSON object that `journal.schema.json` accepts. */
    public fun toJson(journal: Journal): JsonObject =
        JsonObject(
            mapOf(
                JournalFields.ID to JsonPrimitive(journal.id.value),
                JournalFields.IDEMPOTENCY_KEY to JsonPrimitive(journal.idempotencyKey),
                JournalFields.OCCURRED_AT to JsonPrimitive(journal.occurredAt),
                JournalFields.DAY to JsonPrimitive(journal.day),
                JournalFields.KIND to JsonPrimitive(journal.kind.wire),
                JournalFields.DESCRIPTION to JsonPrimitive(journal.description),
                JournalFields.CUSTOMER_ID to (journal.customerId?.let { JsonPrimitive(it.value) } ?: JsonNull),
                JournalFields.LINES to
                    JsonArray(
                        journal.lines.map {
                            JsonObject(
                                mapOf(
                                    JournalFields.ACCOUNT to JsonPrimitive(it.account.key),
                                    JournalFields.AMOUNT_KOBO to JsonPrimitive(it.amount.value),
                                ),
                            )
                        },
                    ),
                JournalFields.REVERSES to (journal.reverses?.let { JsonPrimitive(it.value) } ?: JsonNull),
                JournalFields.SOURCE to
                    JsonObject(
                        mapOf(
                            JournalFields.INFERENCE to JsonPrimitive(journal.source.inference.wire),
                            JournalFields.REASON to JsonPrimitive(journal.source.reason),
                            JournalFields.PROMPT_VERSION to
                                (journal.source.promptVersion?.let(::JsonPrimitive) ?: JsonNull),
                        ),
                    ),
                JournalFields.SCHEMA_VERSION to JsonPrimitive(journal.schemaVersion),
            ),
        )
}

package app.kpakam.core.draft

import app.kpakam.core.money.Kobo
import app.kpakam.core.schema.JsonSchemaValidator
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** The result of checking a model reply. */
public sealed interface DraftValidation {
    /** The reply matches the schema and the domain rules. */
    public data class Valid(val response: DraftResponse) : DraftValidation

    /** The reply is unusable. [errors] go into the retry prompt, one per line. */
    public data class Invalid(val errors: List<String>) : DraftValidation
}

/**
 * Checks raw model output against `draft.schema.json` and the ledger's domain rules.
 *
 * Domain rules (from `ledger.md`): the total must not round to 0 kobo; for a
 * `credit_sale`, `paidNaira` must not exceed `totalNaira`; a `repayment`, or a
 * `credit_sale` that is not fully paid, needs a `customerName`.
 */
public class DraftValidator(schema: JsonObject) {
    private val schemaValidator = JsonSchemaValidator(schema)

    /** Validates [rawOutput], the model's text reply. Never throws. */
    public fun validate(rawOutput: String): DraftValidation {
        val element = parse(rawOutput) ?: return DraftValidation.Invalid(listOf("The reply is not valid JSON"))
        val schemaErrors = schemaValidator.validate(element)
        if (schemaErrors.isNotEmpty()) return DraftValidation.Invalid(schemaErrors)
        val response =
            try {
                JSON.decodeFromJsonElement(DraftResponse.serializer(), element)
            } catch (e: SerializationException) {
                return DraftValidation.Invalid(listOf("The reply does not match the schema: ${e.message}"))
            }
        val domainErrors = response.drafts.flatMapIndexed { index, draft -> domainErrors(index, draft) }
        return if (domainErrors.isEmpty()) DraftValidation.Valid(response) else DraftValidation.Invalid(domainErrors)
    }

    private fun parse(rawOutput: String): JsonElement? =
        try {
            Json.parseToJsonElement(stripCodeFence(rawOutput))
        } catch (e: SerializationException) {
            null
        }

    public companion object {
        private val JSON = Json { ignoreUnknownKeys = false }
        private val FENCE = Regex("^```(?:json)?\\s*([\\s\\S]*?)\\s*```$")

        /** Removes one surrounding Markdown code fence, which small models sometimes add. */
        public fun stripCodeFence(text: String): String {
            val trimmed = text.trim()
            return FENCE.matchEntire(trimmed)?.groupValues?.get(1) ?: trimmed
        }

        /** Returns the domain-rule problems of one draft at position [index]. */
        public fun domainErrors(index: Int, draft: Draft): List<String> {
            val at = "drafts[$index]"
            val total = Kobo.fromNaira(draft.totalNaira)
            if (total.isZero) return listOf("$at.totalNaira rounds to 0 kobo")
            if (draft.kind == DraftKind.REPAYMENT && draft.customerName.isNullOrBlank()) {
                return listOf("$at.customerName is required for a repayment")
            }
            if (draft.kind != DraftKind.CREDIT_SALE) return emptyList()
            val paid = Kobo.fromNaira(draft.paidNaira ?: 0.0)
            return when {
                paid > total -> listOf("$at.paidNaira must not be more than totalNaira")
                paid < total && draft.customerName.isNullOrBlank() ->
                    listOf("$at.customerName is required for a credit_sale")
                else -> emptyList()
            }
        }
    }
}

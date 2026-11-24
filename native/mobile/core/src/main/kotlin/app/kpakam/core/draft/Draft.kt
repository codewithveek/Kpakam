package app.kpakam.core.draft

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What kind of money event a model draft describes (`draft.schema.json`). */
@Serializable
public enum class DraftKind(public val wire: String) {
    @SerialName("sale")
    SALE("sale"),

    @SerialName("credit_sale")
    CREDIT_SALE("credit_sale"),

    @SerialName("repayment")
    REPAYMENT("repayment"),

    @SerialName("expense")
    EXPENSE("expense"),
}

/** One draft entry. Amounts are naira from the model; domain code converts them to kobo once. */
@Serializable
public data class Draft(
    val kind: DraftKind,
    val item: String? = null,
    val quantity: Double? = null,
    val unit: String? = null,
    val totalNaira: Double,
    val paidNaira: Double? = null,
    val customerName: String? = null,
    val confidence: Double,
)

/** What a model returns for one note. */
@Serializable
public data class DraftResponse(
    val drafts: List<Draft>,
    val needsClarification: String? = null,
    val transcript: String? = null,
)

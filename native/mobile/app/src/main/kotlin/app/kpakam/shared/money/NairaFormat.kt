package app.kpakam.shared.money

import app.kpakam.core.money.Kobo
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Formats kobo as naira for the UI: `₦45,000`, `₦12.35`, `−₦1,500`. Uses en-NG grouping. */
object NairaFormat {
    private val locale: Locale = Locale.forLanguageTag("en-NG")
    private const val SIGN = "₦"
    private const val MINUS = "−"

    /** Formats [kobo] with the naira sign. Whole naira show no decimals. */
    fun format(kobo: Kobo): String {
        val naira = BigDecimal.valueOf(kobo.value).abs().movePointLeft(2)
        val pattern = if (kobo.value % 100 == 0L) "#,##0" else "#,##0.00"
        val text = DecimalFormat(pattern, DecimalFormatSymbols.getInstance(locale)).format(naira)
        return if (kobo.value < 0) "$MINUS$SIGN$text" else "$SIGN$text"
    }

    /** Formats [kobo] without the sign, for speech ("45,000"). */
    fun plain(kobo: Kobo): String = format(kobo).removePrefix(MINUS).removePrefix(SIGN)

    /** Formats naira typed by the trader or returned by a model. */
    fun formatNaira(naira: Double): String = format(Kobo.fromNaira(naira))
}

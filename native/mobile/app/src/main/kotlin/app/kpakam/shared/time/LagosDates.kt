package app.kpakam.shared.time

import app.kpakam.core.time.LagosTime
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Dates for the UI, always in Africa/Lagos. */
object LagosDates {
    private val locale: Locale = Locale.forLanguageTag("en-NG")
    private val dayHeader = DateTimeFormatter.ofPattern("EEE d MMM", locale)
    private val timeOfDay = DateTimeFormatter.ofPattern("h:mm a", locale)

    /** Today's Africa/Lagos day as `YYYY-MM-DD`. */
    fun today(clock: Clock): String = LagosTime.dayOf(clock.instant())

    /** The day [days] before today, as `YYYY-MM-DD`. */
    fun daysAgo(clock: Clock, days: Long): String = LocalDate.parse(today(clock)).minusDays(days).toString()

    /** A short day header such as `Tue 24 Nov`. */
    fun dayHeader(day: String): String = LocalDate.parse(day).format(dayHeader)

    /** The time of an `occurredAt` value, such as `9:41 am`. */
    fun time(occurredAt: String): String =
        OffsetDateTime.parse(occurredAt).atZoneSameInstant(LagosTime.ZONE).format(timeOfDay)
}

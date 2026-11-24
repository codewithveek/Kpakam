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
    private val timeOfDay = DateTimeFormatter.ofPattern("h:mm a", locale)
    private val longDay = DateTimeFormatter.ofPattern("EEE, d MMM", locale)
    private val weekday = DateTimeFormatter.ofPattern("EEE", locale)

    /** Today's Africa/Lagos day as `YYYY-MM-DD`. */
    fun today(clock: Clock): String = LagosTime.dayOf(clock.instant())

    /** The time of an `occurredAt` value, such as `9:41 am`. */
    fun time(occurredAt: String): String =
        OffsetDateTime.parse(occurredAt).atZoneSameInstant(LagosTime.ZONE).format(timeOfDay)

    /** A page subtitle such as `Tue, 24 Nov`. */
    fun longDay(day: String): String = LocalDate.parse(day).format(longDay)

    /** The short weekday of [day], such as `Tue`. */
    fun weekday(day: String): String = LocalDate.parse(day).format(weekday)

    /** The day of the month of [day], such as `24`. */
    fun dayOfMonth(day: String): String = LocalDate.parse(day).dayOfMonth.toString()

    /** The [count] days up to and including today, oldest first. */
    fun lastDays(clock: Clock, count: Int): List<String> {
        val today = LocalDate.parse(today(clock))
        return (count - 1 downTo 0).map { today.minusDays(it.toLong()).toString() }
    }

    /** The hour of day in Africa/Lagos, for the greeting. */
    fun hour(clock: Clock): Int = clock.instant().atZone(LagosTime.ZONE).hour
}

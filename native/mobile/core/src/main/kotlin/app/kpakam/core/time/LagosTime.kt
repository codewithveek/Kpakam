package app.kpakam.core.time

import java.time.DayOfWeek
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Dates and times in the trader's time zone, Africa/Lagos (UTC+01:00, no daylight saving). */
public object LagosTime {
    /** The Africa/Lagos zone. */
    public val ZONE: ZoneId = ZoneId.of("Africa/Lagos")

    private val OCCURRED_AT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
    private val DAY: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /** Formats [instant] as ISO 8601 with seconds and the `+01:00` offset, e.g. `2026-11-24T09:41:00+01:00`. */
    public fun occurredAt(instant: Instant): String =
        instant.truncatedTo(ChronoUnit.SECONDS).atZone(ZONE).format(OCCURRED_AT)

    /** Returns the Africa/Lagos calendar day (`YYYY-MM-DD`) of [instant]. */
    public fun dayOf(instant: Instant): String = instant.atZone(ZONE).format(DAY)

    /**
     * Returns the Africa/Lagos day of an `occurredAt` string with any offset.
     * Throws [java.time.format.DateTimeParseException] for a malformed string.
     */
    public fun dayOfOccurredAt(occurredAt: String): String = dayOf(OffsetDateTime.parse(occurredAt).toInstant())

    /** Returns the start (Monday 00:00, Africa/Lagos) of the week that contains [instant]. */
    public fun weekStart(instant: Instant): Instant =
        instant
            .atZone(ZONE)
            .toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(ZONE)
            .toInstant()
}

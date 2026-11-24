package app.kpakam.core.money

import app.kpakam.core.customers.cleanDisplayName
import app.kpakam.core.customers.nameKey
import app.kpakam.core.ids.CustomerId
import app.kpakam.core.ids.Ulid
import app.kpakam.core.time.LagosTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.Random

class ValueTypesTest {
    @Test
    fun `naira converts to kobo with decimal half-up rounding`() {
        assertEquals(Kobo(101), Kobo.fromNaira(1.005))
        assertEquals(Kobo(1235), Kobo.fromNaira(12.345))
        assertEquals(Kobo(4_500_000), Kobo.fromNaira(45000.0))
        assertEquals(Kobo(0), Kobo.fromNaira(0.004))
        assertEquals(Kobo(1), Kobo.fromNaira(0.005))
        assertEquals(Kobo(10_000_000_000), Kobo.fromNaira(100_000_000.0))
        assertThrows<IllegalArgumentException> { Kobo.fromNaira(Double.NaN) }
    }

    @Test
    fun `kobo arithmetic overflows loudly`() {
        assertEquals(Kobo(3), Kobo(1) + Kobo(2))
        assertEquals(Kobo(-2), -Kobo(2))
        assertThrows<ArithmeticException> { Kobo(Long.MAX_VALUE) + Kobo(1) }
    }

    @Test
    fun `ulids are 26 Crockford characters and sort by time`() {
        val random = Random(1)
        val early = Ulid.generate(1_700_000_000_000, random)
        val late = Ulid.generate(1_700_000_000_001, random)
        assertEquals(26, early.value.length)
        assertNotNull(Ulid.parse(early.value))
        assertTrue(early.value < late.value)
        assertNull(Ulid.parse("01JDX6M8Z9Q4W2E3R5T7Y9V1KU"))
        assertNull(Ulid.parse("short"))
        assertNotEquals(Ulid.generate(1, random), Ulid.generate(1, random))
        assertEquals("01JDX6M8Z9Q4W2E3R5T7Y9V1K0", CustomerId.parse("01JDX6M8Z9Q4W2E3R5T7Y9V1K0")?.value)
    }

    @Test
    fun `ulid time prefix encodes milliseconds`() {
        assertTrue(Ulid.generate(0, Random(1)).value.startsWith("0000000000"))
        assertTrue(Ulid.generate((1L shl 48) - 1, Random(1)).value.startsWith("7ZZZZZZZZZ"))
        assertThrows<IllegalArgumentException> { Ulid.generate(-1) }
    }

    @Test
    fun `name keys are lower-case, trimmed, single-spaced`() {
        assertEquals("mama bisi", nameKey("  Mama \t  Bisi "))
        assertEquals("Mama Bisi", cleanDisplayName("  Mama   Bisi "))
    }

    @Test
    fun `occurredAt and day use Africa Lagos`() {
        val instant = Instant.parse("2026-11-24T23:30:15.789Z")
        assertEquals("2026-11-25T00:30:15+01:00", LagosTime.occurredAt(instant))
        assertEquals("2026-11-25", LagosTime.dayOf(instant))
        assertEquals("2026-11-25", LagosTime.dayOfOccurredAt("2026-11-24T23:30:15Z"))
        assertEquals(Instant.parse("2026-11-22T23:00:00Z"), LagosTime.weekStart(instant))
    }
}

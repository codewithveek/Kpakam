package app.kpakam.shared

import app.kpakam.core.money.Kobo
import app.kpakam.shared.money.NairaFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class NairaFormatTest {
    @Test
    fun formatsNairaWithGroupingAndOptionalKobo() {
        assertEquals("₦45,000", NairaFormat.format(Kobo(4_500_000)))
        assertEquals("₦12.35", NairaFormat.format(Kobo(1235)))
        assertEquals("−₦1,500", NairaFormat.format(Kobo(-150_000)))
        assertEquals("₦0", NairaFormat.format(Kobo.ZERO))
        assertEquals("45,000", NairaFormat.plain(Kobo(4_500_000)))
    }
}

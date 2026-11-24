package app.kpakam.shared.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class InitialsTest {
    @Test
    fun `keeps the title when the name has one other word`() {
        assertEquals("MB", Initials.of("Mama Bisi"))
    }

    @Test
    fun `drops titles when two real names remain`() {
        assertEquals("BA", Initials.of("Mama Bisi Adeyemi"))
    }

    @Test
    fun `single and empty names`() {
        assertEquals("T", Initials.of("tunde"))
        assertEquals("?", Initials.of("   "))
    }
}

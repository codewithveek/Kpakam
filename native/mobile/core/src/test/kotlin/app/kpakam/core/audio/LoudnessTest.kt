package app.kpakam.core.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoudnessTest {
    private fun pcm(vararg samples: Int): ByteArray =
        ByteArray(samples.size * 2).also { bytes ->
            samples.forEachIndexed { i, s ->
                bytes[2 * i] = (s and 0xFF).toByte()
                bytes[2 * i + 1] = (s shr 8).toByte()
            }
        }

    @Test
    fun `silence is zero and a loud square wave is full`() {
        assertEquals(0f, Loudness.ofPcm16(pcm(0, 0, 0, 0)))
        assertEquals(1f, Loudness.ofPcm16(pcm(20_000, -20_000, 20_000, -20_000)))
    }

    @Test
    fun `quiet speech sits in the middle of the range`() {
        val level = Loudness.ofPcm16(pcm(2_000, -2_000, 2_000, -2_000))
        assertEquals(0.244f, level, 0.01f)
    }

    @Test
    fun `only the given length is read, and odd bytes are ignored`() {
        assertEquals(0f, Loudness.ofPcm16(pcm(0, 30_000), length = 2))
        assertEquals(0f, Loudness.ofPcm16(ByteArray(1)))
    }

    @Test
    fun `peak and recogniser levels are clamped to 0 to 1`() {
        assertEquals(0f, Loudness.ofPeak(0))
        assertEquals(1f, Loudness.ofPeak(32_767))
        assertEquals(0f, Loudness.ofRecognizerDb(-10f))
        assertEquals(1f, Loudness.ofRecognizerDb(20f))
        assertTrue(Loudness.ofRecognizerDb(4f) in 0.4f..0.6f)
    }
}

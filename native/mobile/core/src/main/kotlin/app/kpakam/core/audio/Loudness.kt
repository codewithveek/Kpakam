package app.kpakam.core.audio

import kotlin.math.sqrt

/** Microphone loudness for the recording sound wave, always scaled to 0..1 (web soundwave.tsx). */
public object Loudness {
    private const val FULL_SCALE = 32_768.0
    private const val GAIN = 4.0
    private const val PEAK_GAIN = 1.2
    private const val DB_FLOOR = -2.0
    private const val DB_RANGE = 12.0
    private const val BYTE_MASK = 0xFF
    private const val BITS_PER_BYTE = 8

    /**
     * Root-mean-square loudness of 16-bit little-endian mono PCM in [pcm] (the first [length] bytes),
     * scaled so normal speech fills most of the range.
     */
    public fun ofPcm16(pcm: ByteArray, length: Int = pcm.size): Float {
        val samples = minOf(length, pcm.size) / 2
        if (samples == 0) return 0f
        var sum = 0.0
        for (i in 0 until samples) {
            val low = pcm[2 * i].toInt() and BYTE_MASK
            val high = pcm[2 * i + 1].toInt()
            val value = ((high shl BITS_PER_BYTE) or low) / FULL_SCALE
            sum += value * value
        }
        return clamp(sqrt(sum / samples) * GAIN)
    }

    /** Loudness from a recorder's peak amplitude (0..32767) since the last reading. */
    public fun ofPeak(amplitude: Int): Float = clamp(amplitude / FULL_SCALE * PEAK_GAIN)

    /** Loudness from the speech recogniser's `onRmsChanged` value (about -2..10 dB). */
    public fun ofRecognizerDb(rmsDb: Float): Float = clamp((rmsDb - DB_FLOOR) / DB_RANGE)

    private fun clamp(value: Double): Float = value.coerceIn(0.0, 1.0).toFloat()
}

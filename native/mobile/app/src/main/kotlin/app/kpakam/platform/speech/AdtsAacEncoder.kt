package app.kpakam.platform.speech

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import java.io.OutputStream

/**
 * Encodes 16-bit mono PCM to AAC-LC and writes a raw ADTS stream (`audio/aac`), which the
 * Gemini API accepts. ADTS needs no container, so each frame is a 7-byte header plus data.
 */
class AdtsAacEncoder(private val sampleRate: Int, bitRate: Int, private val out: OutputStream) {
    private val codec: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    private val info = MediaCodec.BufferInfo()
    private var samplesQueued = 0L

    init {
        val format =
            MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT)
            }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
    }

    /** Encodes [length] bytes of PCM from [pcm]. */
    fun encode(pcm: ByteArray, length: Int) {
        var offset = 0
        while (offset < length) {
            val index = codec.dequeueInputBuffer(TIMEOUT_US)
            if (index < 0) {
                drain()
                continue
            }
            val buffer = checkNotNull(codec.getInputBuffer(index))
            val chunk = minOf(buffer.remaining(), length - offset)
            buffer.put(pcm, offset, chunk)
            codec.queueInputBuffer(index, 0, chunk, presentationUs(), 0)
            samplesQueued += chunk / BYTES_PER_SAMPLE
            offset += chunk
            drain()
        }
    }

    /** Flushes the encoder and releases it. The caller closes [out]. */
    fun finish() {
        try {
            val index = codec.dequeueInputBuffer(TIMEOUT_US)
            if (index >= 0) codec.queueInputBuffer(index, 0, 0, presentationUs(), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            repeat(MAX_DRAIN_ROUNDS) { if (drain(endOfStream = true)) return@repeat }
        } finally {
            codec.stop()
            codec.release()
        }
    }

    /** Writes every ready output frame. Returns `true` once the end of stream came out. */
    private fun drain(endOfStream: Boolean = false): Boolean {
        while (true) {
            val index = codec.dequeueOutputBuffer(info, if (endOfStream) TIMEOUT_US else 0)
            if (index < 0) return false
            val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
            if (!isConfig && info.size > 0) {
                val data = ByteArray(info.size)
                checkNotNull(codec.getOutputBuffer(index)).apply { position(info.offset) }.get(data)
                out.write(adtsHeader(info.size))
                out.write(data)
            }
            codec.releaseOutputBuffer(index, false)
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return true
        }
    }

    private fun presentationUs(): Long = samplesQueued * MICROS / sampleRate

    /** The 7-byte ADTS header for one AAC-LC frame; the numbers are the fixed bit layout from ISO/IEC 13818-7. */
    @Suppress("MagicNumber")
    private fun adtsHeader(payload: Int): ByteArray {
        val length = payload + HEADER_BYTES
        val freqIndex = FREQ_INDEX.getValue(sampleRate)
        return byteArrayOf(
            0xFF.toByte(),
            0xF1.toByte(),
            (((AAC_LC - 1) shl 6) or (freqIndex shl 2) or (MONO shr 2)).toByte(),
            (((MONO and 3) shl 6) or (length shr 11)).toByte(),
            ((length and 0x7FF) shr 3).toByte(),
            (((length and 7) shl 5) or 0x1F).toByte(),
            0xFC.toByte(),
        )
    }

    private companion object {
        const val TIMEOUT_US = 10_000L
        const val MAX_INPUT = 16_384
        const val MAX_DRAIN_ROUNDS = 50
        const val BYTES_PER_SAMPLE = 2
        const val MICROS = 1_000_000L
        const val HEADER_BYTES = 7
        const val AAC_LC = 2
        const val MONO = 1
        val FREQ_INDEX = mapOf(48_000 to 3, 44_100 to 4, 32_000 to 5, 24_000 to 6, 22_050 to 7, 16_000 to 8)
    }
}

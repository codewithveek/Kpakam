package app.kpakam.platform.speech

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.annotation.RequiresApi
import app.kpakam.core.audio.Loudness
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One microphone capture with two outputs: raw PCM for the on-device recogniser and an AAC
 * clip on disk, in case the mixed-speech check says the cloud model must read it.
 */
interface SharedCapture {
    /** Stops the capture (if still running) and returns the clip, or `null` when nothing was recorded. */
    suspend fun finish(): VoiceClip?
}

/** Opens a [SharedCapture] when the platform lets the recogniser and the app share the mic. */
interface SharedMic {
    /** `true` on Android 13+, where the recogniser can read audio the app captures (EXTRA_AUDIO_SOURCE). */
    val supported: Boolean

    /** Starts capturing to a clip named [name]. Returns `null` when the mic cannot be opened. */
    fun open(name: String): SharedCapture?
}

/** AudioRecord adapter for [SharedMic]. */
@Singleton
class AndroidSharedMic
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val micLevel: MicLevel,
    ) : SharedMic {
        override val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        override fun open(name: String): SharedCapture? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
            val file = File(context.filesDir, "voice").apply { mkdirs() }.resolve("$name.aac")
            return try {
                PipedCapture.start(file, micLevel::report)
            } catch (e: IOException) {
                Log.w(TAG, "Could not open the shared mic", e)
                null
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Could not open the shared mic", e)
                null
            }
        }

        private companion object {
            const val TAG = "SharedMic"
        }
    }

/** Captures PCM, writes it to a pipe the recogniser reads, and encodes the same audio to AAC. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class PipedCapture private constructor(
    private val record: AudioRecord,
    private val file: File,
    private val toRecognizer: OutputStream,
    /** The read end of the pipe, passed to the recogniser as `EXTRA_AUDIO_SOURCE`. */
    val recognizerAudio: ParcelFileDescriptor,
    private val onLevel: (Float) -> Unit,
) : SharedCapture {
    private val stopRequested = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null

    private fun run() {
        loop =
            scope.launch {
                file.outputStream().buffered().use { out ->
                    val encoder = AdtsAacEncoder(SAMPLE_RATE, BIT_RATE, out)
                    try {
                        pump(encoder)
                    } finally {
                        encoder.finish()
                    }
                }
            }
    }

    private fun pump(encoder: AdtsAacEncoder) {
        val buffer = ByteArray(CHUNK_BYTES)
        var recognizerOpen = true
        var bytes = 0L
        record.startRecording()
        try {
            while (!stopRequested.get() && bytes < MAX_BYTES) {
                val read = record.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                bytes += read
                onLevel(Loudness.ofPcm16(buffer, read))
                encoder.encode(buffer, read)
                if (recognizerOpen) recognizerOpen = writeToRecognizer(buffer, read)
            }
        } finally {
            record.stop()
            record.release()
            closeQuietly()
        }
    }

    private fun writeToRecognizer(buffer: ByteArray, length: Int): Boolean =
        try {
            toRecognizer.write(buffer, 0, length)
            true
        } catch (e: IOException) {
            false // The recogniser finished and closed its end; keep recording the clip.
        }

    private fun closeQuietly() {
        try {
            toRecognizer.close()
        } catch (e: IOException) {
            Log.d(TAG, "Pipe already closed", e)
        }
    }

    override suspend fun finish(): VoiceClip? =
        withContext(Dispatchers.IO) {
            stopRequested.set(true)
            loop?.join()
            recognizerAudio.close()
            if (file.length() > 0) VoiceClip(file.path, MIME_TYPE, file.length()) else null
        }

    companion object {
        private const val TAG = "PipedCapture"
        const val SAMPLE_RATE = 16_000
        private const val BIT_RATE = 24_000
        private const val CHUNK_BYTES = 3_200 // 100 ms of 16 kHz, 16-bit mono
        private const val MAX_BYTES = SAMPLE_RATE * 2L * 30 // 30 seconds
        private const val MIME_TYPE = "audio/aac"

        /** Opens the mic and starts capturing. The caller already holds RECORD_AUDIO. */
        @SuppressLint("MissingPermission")
        fun start(file: File, onLevel: (Float) -> Unit): PipedCapture {
            val minBuffer =
                AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
            val record =
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuffer, CHUNK_BYTES * 2),
                )
            check(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord did not initialise" }
            val (readEnd, writeEnd) = ParcelFileDescriptor.createPipe()
            return PipedCapture(record, file, ParcelFileDescriptor.AutoCloseOutputStream(writeEnd), readEnd, onLevel)
                .also { it.run() }
        }
    }
}

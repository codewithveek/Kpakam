package app.kpakam.platform.speech

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** A recorded voice clip on disk. */
data class VoiceClip(val path: String, val mimeType: String, val sizeBytes: Long)

/** Records a short, compressed clip for the cloud model (SPEECH_CLOUD). */
interface VoiceRecorder {
    /** Starts recording to a new file named [name]. [onLimit] runs when 30 seconds pass. Returns `false` on failure. */
    fun start(name: String, onLimit: () -> Unit): Boolean

    /** Stops recording and returns the clip, or `null` when nothing usable was recorded. */
    fun stop(): VoiceClip?
}

/**
 * MediaRecorder adapter. Android 10+ records Opus in Ogg (`audio/ogg`), which keeps a
 * 30-second clip near 60 KB. Older phones record AAC (`audio/aac`, ADTS stream), a
 * format the Gemini API accepts.
 */
@Singleton
class AndroidVoiceRecorder
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : VoiceRecorder {
        private var recorder: MediaRecorder? = null
        private var current: VoiceClip? = null

        override fun start(name: String, onLimit: () -> Unit): Boolean {
            val opus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            val file =
                File(
                    context.filesDir,
                    "voice",
                ).apply { mkdirs() }.resolve(if (opus) "$name.ogg" else "$name.aac")
            val created = newRecorder()
            return try {
                created.configure(opus, file)
                created.setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onLimit()
                }
                created.prepare()
                created.start()
                recorder = created
                current = VoiceClip(file.path, if (opus) "audio/ogg" else "audio/aac", 0)
                true
            } catch (e: IOException) {
                created.release()
                false
            } catch (e: IllegalStateException) {
                created.release()
                false
            }
        }

        override fun stop(): VoiceClip? {
            val active = recorder ?: return null
            recorder = null
            val stopped =
                try {
                    active.stop()
                    true
                } catch (e: RuntimeException) {
                    false // stop() throws when no audio was captured.
                } finally {
                    active.release()
                }
            val clip = current?.takeIf { stopped } ?: return null
            val file = File(clip.path)
            return if (file.length() > 0) clip.copy(sizeBytes = file.length()) else null
        }

        @Suppress("DEPRECATION")
        private fun newRecorder(): MediaRecorder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()

        private fun MediaRecorder.configure(opus: Boolean, file: File) {
            setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            if (opus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setOutputFormat(MediaRecorder.OutputFormat.OGG)
                setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            } else {
                setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            }
            setAudioChannels(1)
            setAudioSamplingRate(SAMPLE_RATE)
            setAudioEncodingBitRate(BIT_RATE)
            setMaxDuration(MAX_DURATION_MS)
            setOutputFile(file.path)
        }

        private companion object {
            const val SAMPLE_RATE = 16_000
            const val BIT_RATE = 16_000
            const val MAX_DURATION_MS = 30_000
        }
    }

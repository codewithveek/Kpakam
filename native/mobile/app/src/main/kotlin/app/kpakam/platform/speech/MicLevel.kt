package app.kpakam.platform.speech

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The live microphone loudness (0..1) while Kpakam listens or records, for the sound wave.
 * The shared capture, the recorder, or the recogniser reports it, whichever owns the microphone.
 */
@Singleton
class MicLevel
    @Inject
    constructor() {
        private val current = MutableStateFlow(0f)
        val level: StateFlow<Float> = current.asStateFlow()

        fun report(value: Float) {
            current.value = value
        }

        fun reset() {
            current.value = 0f
        }
    }

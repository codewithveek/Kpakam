package app.kpakam.features.quickentry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kpakam.core.routing.RouteDecision
import app.kpakam.inference.ParseOutcome
import app.kpakam.inference.VoiceListen
import app.kpakam.inference.VoiceService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/**
 * The voice screen (F8). The routing stays in [VoiceService]: in auto or English mode with a phone
 * recogniser, the phone listens and shows the words live (auto mode also keeps the clip for the
 * mixed-speech check); otherwise a clip is recorded for online reading or saved in the queue.
 */
@HiltViewModel
class VoiceViewModel
    @Inject
    constructor(
        private val voice: VoiceService,
        private val handoff: VoiceHandoff,
        private val clock: Clock,
    ) : ViewModel() {
        private val ui = MutableStateFlow<VoiceUi>(VoiceUi.Closed)
        val state: StateFlow<VoiceUi> = ui.asStateFlow()

        /** The live microphone loudness for the sound wave. */
        val level: StateFlow<Float> = voice.micLevel

        private var listenJob: Job? = null
        private var recordingDecision: RouteDecision? = null

        /** Counts takes, so a note still being read from an earlier take never changes a new one. */
        private var take = 0

        /** Opens the screen and starts listening. The caller already holds the microphone permission. */
        fun open() {
            if (ui.value.open && ui.value !is VoiceUi.Finished && ui.value !is VoiceUi.Review) return
            take += 1
            ui.value = VoiceUi.Preparing
            listenJob = viewModelScope.launch { start() }
        }

        /** The microphone permission was refused. */
        fun onMicDenied() {
            ui.value = VoiceUi.Finished(VoiceNote.MicDenied)
        }

        /** Done: stop listening and show the words (phone) or send the clip (online). */
        fun finish() {
            val current = ui.value as? VoiceUi.Listening ?: return
            if (current.live) voice.stopListening() else finishRecording()
        }

        /** Close or Cancel: nothing is saved. A note already being read keeps going in the background. */
        fun close() {
            when (val current = ui.value) {
                is VoiceUi.Listening -> if (current.live) listenJob?.cancel() else cancelRecording()
                VoiceUi.Preparing -> listenJob?.cancel()
                else -> Unit
            }
            ui.value = VoiceUi.Closed
        }

        /** Say again: start a new take. */
        fun again() {
            close()
            open()
        }

        /** The trader fixed the words by hand. */
        fun editWords(words: String) =
            ui.update {
                if (it is VoiceUi.Review) {
                    it.copy(
                        words = words.take(QuickEntryUiState.MAX_NOTE_CHARS),
                    )
                } else {
                    it
                }
            }

        /** Send: the checked words are read like a typed note. */
        fun send() {
            val review = ui.value as? VoiceUi.Review ?: return
            if (review.words.isBlank()) return
            ui.value = VoiceUi.Closed
            handoff.send(VoiceResult.Words(review.words.trim()))
        }

        private suspend fun start() {
            val decision = voice.decide()
            if (decision is RouteDecision.Device) {
                listenOnDevice()
            } else if (voice.startRecording(onLimit = ::finishRecording)) {
                recordingDecision = decision
                ui.value = VoiceUi.Listening(live = false, startedAtMs = clock.millis())
            } else {
                ui.value = VoiceUi.Finished(VoiceNote.ReadFailed)
            }
        }

        private suspend fun listenOnDevice() {
            val startedAt = clock.millis()
            ui.value = VoiceUi.Listening(live = true, startedAtMs = startedAt)
            val result =
                voice.listenOnDevice(
                    onPartial = { words ->
                        ui.update { if (it is VoiceUi.Listening) it.copy(partial = words) else it }
                    },
                    onSendingClip = { ui.update { if (it.open) VoiceUi.Processing(mixed = true) else it } },
                )
            when (result) {
                is VoiceListen.Transcript ->
                    ui.update {
                        if (!it.open) return@update it
                        val seconds = ((clock.millis() - startedAt) / MS_PER_SECOND).toInt()
                        VoiceUi.Review(result.text.take(QuickEntryUiState.MAX_NOTE_CHARS), seconds)
                    }
                VoiceListen.NothingHeard -> ui.update { if (it.open) VoiceUi.Finished(VoiceNote.NothingHeard) else it }
                is VoiceListen.SentClip -> onRead(result.outcome, take)
            }
        }

        private fun finishRecording() {
            val decision = recordingDecision ?: return
            recordingDecision = null
            ui.value = VoiceUi.Processing(mixed = false)
            val thisTake = take
            viewModelScope.launch { onRead(voice.finishRecording(decision), thisTake) }
        }

        private fun cancelRecording() {
            recordingDecision = null
            voice.cancelRecording()
        }

        /** Drafts go to Home and the screen closes; a queued or failed note is shown here first. */
        private fun onRead(outcome: ParseOutcome, fromTake: Int) {
            val stillOpen = ui.value.open && fromTake == take
            when (outcome) {
                is ParseOutcome.Drafts -> {
                    handoff.send(VoiceResult.Read(outcome))
                    if (stillOpen) ui.value = VoiceUi.Closed
                }
                is ParseOutcome.Queued -> if (stillOpen) showThenClose(VoiceNote.Queued(outcome.queueReason))
                is ParseOutcome.Failed -> if (stillOpen) ui.value = VoiceUi.Finished(VoiceNote.ReadFailed)
            }
        }

        private fun showThenClose(note: VoiceNote) {
            val shown = VoiceUi.Finished(note)
            ui.value = shown
            viewModelScope.launch {
                delay(AUTO_CLOSE_MS)
                ui.update { if (it === shown) VoiceUi.Closed else it }
            }
        }

        override fun onCleared() {
            if (ui.value is VoiceUi.Listening && recordingDecision != null) voice.cancelRecording()
        }

        companion object {
            /** The longest voice note, in seconds. */
            const val MAX_SECONDS = 30
            private const val AUTO_CLOSE_MS = 1_800L
            private const val MS_PER_SECOND = 1_000L
        }
    }

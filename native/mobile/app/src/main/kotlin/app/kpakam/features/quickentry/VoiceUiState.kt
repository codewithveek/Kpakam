package app.kpakam.features.quickentry

import app.kpakam.core.routing.QueueReason
import app.kpakam.inference.ParseOutcome
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What the voice screen shows when it is done: a plain sentence, sometimes an error. */
sealed interface VoiceNote {
    /** The clip waits in the queue (offline, data saver, and so on). */
    data class Queued(val queueReason: QueueReason) : VoiceNote

    data object NothingHeard : VoiceNote

    data object ReadFailed : VoiceNote

    data object MicDenied : VoiceNote
}

/** Where the full-screen voice screen is (web use-voice-entry.ts). */
sealed interface VoiceUi {
    data object Closed : VoiceUi

    /** Getting the microphone ready. */
    data object Preparing : VoiceUi

    /** [live] is true when the phone writes the words down as the trader speaks. */
    data class Listening(val live: Boolean, val startedAtMs: Long, val partial: String = "") : VoiceUi

    /** Reading the voice note; [mixed] when mixed languages were heard and the clip went online. */
    data class Processing(val mixed: Boolean) : VoiceUi

    /** The trader checks (and can fix) the words before Kpakam reads them. */
    data class Review(val words: String, val seconds: Int) : VoiceUi

    data class Finished(val note: VoiceNote) : VoiceUi {
        val isError: Boolean get() = note !is VoiceNote.Queued
    }

    /** The screen is open and showing something. */
    val open: Boolean get() = this != Closed
}

/** What the voice screen hands to quick entry when it closes. */
sealed interface VoiceResult {
    /** Words the trader checked: read them like a typed note. */
    data class Words(val text: String) : VoiceResult

    /** A voice note was read online: show its drafts. */
    data class Read(val outcome: ParseOutcome) : VoiceResult
}

/** Passes voice results from the voice screen to quick entry on Home. */
@Singleton
class VoiceHandoff
    @Inject
    constructor() {
        private val channel = Channel<VoiceResult>(Channel.BUFFERED)
        val results: Flow<VoiceResult> = channel.receiveAsFlow()

        fun send(result: VoiceResult) {
            channel.trySend(result)
        }
    }

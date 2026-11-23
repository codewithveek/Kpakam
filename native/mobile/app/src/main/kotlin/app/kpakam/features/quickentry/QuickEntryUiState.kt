package app.kpakam.features.quickentry

import app.kpakam.core.draft.Draft
import app.kpakam.core.ids.Ulid
import app.kpakam.core.ledger.LedgerError
import app.kpakam.core.routing.QueueReason
import app.kpakam.inference.DraftOrigin
import app.kpakam.inference.queue.AiJob

/** One draft on the review list. [journalId] is fixed when the draft appears, for idempotent saves. */
data class DraftUi(
    val journalId: Ulid,
    val idempotencyKey: String,
    val draft: Draft,
    val error: LedgerError? = null,
)

/** What the quick-entry screen is doing. */
sealed interface EntryPhase {
    data object Idle : EntryPhase

    data object Parsing : EntryPhase

    data object Listening : EntryPhase

    data object Recording : EntryPhase

    data object Saving : EntryPhase
}

/** A one-off message for the trader. */
sealed interface EntryMessage {
    data class Clarify(val question: String) : EntryMessage

    data object ReadFailed : EntryMessage

    data class Queued(val queueReason: QueueReason, val voice: Boolean = false) : EntryMessage

    data class Saved(val count: Int) : EntryMessage

    data object AlreadySaved : EntryMessage

    data object NoSpeech : EntryMessage

    /** The phone wrote down what it heard; the trader checks the words first. */
    data object CheckWords : EntryMessage

    /** Mixed languages heard: the voice note is being read online. */
    data object Mixed : EntryMessage

    data object MicDenied : EntryMessage

    data object SaveFailed : EntryMessage
}

/** The whole quick-entry screen state. */
data class QuickEntryUiState(
    val note: String = "",
    val phase: EntryPhase = EntryPhase.Idle,
    val drafts: List<DraftUi> = emptyList(),
    val origin: DraftOrigin? = null,
    val transcript: String? = null,
    val reviewingJobId: String? = null,
    val editingIndex: Int? = null,
    val message: EntryMessage? = null,
    val jobs: List<AiJob> = emptyList(),
    val online: Boolean = true,
) {
    val canSubmit: Boolean get() = note.isNotBlank() && phase == EntryPhase.Idle
    val busy: Boolean get() = phase != EntryPhase.Idle

    companion object {
        /** F1: a note is at most 280 characters. */
        const val MAX_NOTE_CHARS = 280

        /** Below this model confidence a draft gets a "check carefully" mark. */
        const val LOW_CONFIDENCE = 0.6
    }
}

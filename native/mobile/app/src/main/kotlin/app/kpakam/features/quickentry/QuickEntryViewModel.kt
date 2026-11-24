package app.kpakam.features.quickentry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kpakam.R
import app.kpakam.core.draft.Draft
import app.kpakam.core.draft.DraftResponse
import app.kpakam.core.ids.IdGenerator
import app.kpakam.core.ids.Ulid
import app.kpakam.features.ledger.CorrectionUseCase
import app.kpakam.inference.DraftOrigin
import app.kpakam.inference.ParseOutcome
import app.kpakam.inference.ParseService
import app.kpakam.inference.VoiceService
import app.kpakam.inference.queue.AiJob
import app.kpakam.inference.queue.AiJobQueue
import app.kpakam.shared.ui.ToastAction
import app.kpakam.shared.ui.ToastCenter
import app.kpakam.shared.ui.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Quick entry (F1), voice results (F8), and the pending job list (F7). Unidirectional: events in, state out. */
@HiltViewModel
class QuickEntryViewModel
    @Inject
    constructor(
        private val parse: ParseService,
        private val voice: VoiceService,
        private val confirm: ConfirmDraftsUseCase,
        private val corrections: CorrectionUseCase,
        private val queue: AiJobQueue,
        private val ids: IdGenerator,
        private val toasts: ToastCenter,
        handoff: VoiceHandoff,
    ) : ViewModel() {
        private val local = MutableStateFlow(QuickEntryUiState())

        val state: StateFlow<QuickEntryUiState> =
            combine(local, queue.open) { state, jobs -> state.copy(jobs = jobs) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), QuickEntryUiState())

        init {
            viewModelScope.launch {
                handoff.results.collect { result ->
                    when (result) {
                        is VoiceResult.Words -> submitText(result.text)
                        is VoiceResult.Read -> show(result.outcome)
                    }
                }
            }
        }

        fun onNoteChange(text: String) =
            local.update { it.copy(note = text.take(QuickEntryUiState.MAX_NOTE_CHARS), message = null) }

        /** An example chip such as "Sold…" adds its words to the note. */
        fun addExample(text: String) =
            local.update { it.copy(note = (it.note + text).trimStart().take(QuickEntryUiState.MAX_NOTE_CHARS)) }

        fun submit() {
            val current = local.value
            if (!current.canSubmit) return
            local.update { it.copy(phase = EntryPhase.Parsing, message = null, drafts = emptyList()) }
            viewModelScope.launch { show(parse.parseNote(current.note.trim())) }
        }

        fun startEdit(index: Int) = local.update { it.copy(editingIndex = index) }

        fun cancelEdit() = local.update { it.copy(editingIndex = null) }

        fun saveEdit(index: Int, draft: Draft) =
            local.update { state ->
                state.copy(
                    editingIndex = null,
                    drafts =
                        state.drafts.replaceAt(index) { it.copy(draft = draft, error = null) },
                )
            }

        fun removeDraft(index: Int) =
            local.update { state ->
                val remaining = state.drafts.filterIndexed { i, _ -> i != index }
                if (remaining.isEmpty()) state.cleared(null) else state.copy(drafts = remaining)
            }

        fun discardDrafts() {
            local.value.reviewingJobId?.let { viewModelScope.launch { queue.markReviewed(it) } }
            local.update { it.cleared(null) }
        }

        fun saveAll() {
            val current = local.value
            val origin = current.origin ?: return
            if (current.drafts.isEmpty() || current.busy) return
            local.update { it.copy(phase = EntryPhase.Saving) }
            viewModelScope.launch {
                val items = current.drafts.map { ConfirmableDraft(it.draft, it.journalId, it.idempotencyKey) }
                onSaved(confirm.confirm(items, origin), current.reviewingJobId, items.map { it.journalId })
            }
        }

        fun readAloud(text: String) = voice.readAloud(text)

        fun review(job: AiJob) {
            val result = job.result ?: return
            local.update { it.withDrafts(result.response, result.origin, job.id).copy(reviewingJobId = job.id) }
        }

        fun sendNow(jobId: String) = viewModelScope.launch { queue.approve(jobId) }

        fun retryJob(jobId: String) = viewModelScope.launch { queue.retry(jobId) }

        fun cancelJob(jobId: String) = viewModelScope.launch { queue.cancel(jobId) }

        fun dismissMessage() = local.update { it.copy(message = null) }

        /** Words checked on the voice screen are read like a typed note. */
        private fun submitText(text: String) {
            if (local.value.busy) return
            local.update { it.copy(note = text.take(QuickEntryUiState.MAX_NOTE_CHARS), message = null) }
            submit()
        }

        private suspend fun onSaved(result: ConfirmResult, jobId: String?, journals: List<Ulid>) {
            when (result) {
                is ConfirmResult.Saved -> {
                    jobId?.let { queue.markReviewed(it) }
                    local.update { it.cleared(null) }
                    toasts.show(
                        UiText.Plural(R.plurals.drafts_saved, result.count),
                        ToastAction(R.string.history_undo) { undo(journals) },
                    )
                }
                ConfirmResult.AlreadySaved -> {
                    jobId?.let { queue.markReviewed(it) }
                    local.update { it.cleared(null) }
                    toasts.show(UiText.Res(R.string.drafts_duplicate))
                }
                is ConfirmResult.Invalid ->
                    local.update { state ->
                        state.copy(
                            phase = EntryPhase.Idle,
                            drafts = state.drafts.replaceAt(result.index) { it.copy(error = result.error) },
                        )
                    }
                is ConfirmResult.Failed ->
                    local.update { it.copy(phase = EntryPhase.Idle, message = EntryMessage.SaveFailed) }
            }
        }

        /** Undo from the "Entry saved" toast: every saved entry gets a reversal. */
        private fun undo(journals: List<Ulid>) {
            viewModelScope.launch {
                journals.forEach { corrections.undo(it) }
                toasts.show(UiText.Res(R.string.history_undone))
            }
        }

        private fun show(outcome: ParseOutcome) =
            local.update { state ->
                when (outcome) {
                    is ParseOutcome.Drafts -> state.withDrafts(outcome.response, outcome.origin, jobId = null)
                    is ParseOutcome.Queued ->
                        state.copy(
                            phase = EntryPhase.Idle,
                            note = "",
                            message = EntryMessage.Queued(outcome.queueReason),
                        )
                    is ParseOutcome.Failed -> state.copy(phase = EntryPhase.Idle, message = EntryMessage.ReadFailed)
                }
            }

        private fun QuickEntryUiState.withDrafts(
            response: DraftResponse,
            origin: DraftOrigin,
            jobId: String?,
        ): QuickEntryUiState {
            val requestId = jobId ?: ids.next().value
            val drafts = response.drafts.mapIndexed { index, draft -> DraftUi(ids.next(), "$requestId:$index", draft) }
            val question = response.needsClarification?.takeIf { drafts.isEmpty() }
            return copy(
                phase = EntryPhase.Idle,
                note = if (drafts.isEmpty()) note else "",
                drafts = drafts,
                origin = origin,
                transcript = response.transcript,
                message =
                    when {
                        question != null -> EntryMessage.Clarify(question)
                        drafts.isEmpty() -> EntryMessage.ReadFailed
                        else -> null
                    },
            )
        }

        private fun QuickEntryUiState.cleared(message: EntryMessage?) =
            copy(
                note = "",
                phase = EntryPhase.Idle,
                drafts = emptyList(),
                origin = null,
                transcript = null,
                reviewingJobId = null,
                editingIndex = null,
                message = message,
            )

        private fun List<DraftUi>.replaceAt(index: Int, change: (DraftUi) -> DraftUi): List<DraftUi> =
            mapIndexed { i, item -> if (i == index) change(item) else item }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

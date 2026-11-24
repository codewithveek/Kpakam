package app.kpakam.features.quickentry

import app.kpakam.core.common.Outcome
import app.kpakam.core.draft.Draft
import app.kpakam.core.ids.IdGenerator
import app.kpakam.core.ids.Ulid
import app.kpakam.core.ledger.Journal
import app.kpakam.core.ledger.JournalBuilder
import app.kpakam.core.ledger.JournalRequest
import app.kpakam.core.ledger.JournalSource
import app.kpakam.core.ledger.LedgerError
import app.kpakam.features.customers.CustomerRepository
import app.kpakam.features.customers.CustomerResolver
import app.kpakam.features.ledger.LedgerRepository
import app.kpakam.features.ledger.LedgerWrite
import app.kpakam.features.ledger.WriteResult
import app.kpakam.inference.DraftOrigin
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** A draft the trader confirmed. The ids are fixed when the draft appears, so a double tap saves once. */
data class ConfirmableDraft(val draft: Draft, val journalId: Ulid, val idempotencyKey: String)

/** The result of saving confirmed drafts. */
sealed interface ConfirmResult {
    data class Saved(val count: Int) : ConfirmResult

    /** Every journal already existed: an idempotent no-op. */
    data object AlreadySaved : ConfirmResult

    /** Draft [index] cannot become a journal. Nothing was saved. */
    data class Invalid(val index: Int, val error: LedgerError) : ConfirmResult

    data class Failed(val reason: String) : ConfirmResult
}

/** Use case: confirm drafts and save them as journals (F1, F2, F3). */
@Singleton
class ConfirmDraftsUseCase
    @Inject
    constructor(
        private val ledger: LedgerRepository,
        private val customers: CustomerRepository,
        private val ids: IdGenerator,
        private val clock: Clock,
    ) {
        /** Builds every journal first; saves all of them in one batch only when all are valid. */
        suspend fun confirm(drafts: List<ConfirmableDraft>, origin: DraftOrigin): ConfirmResult =
            confirm(drafts, JournalSource(origin.source, origin.reason.name, origin.promptVersion))

        /** Saves drafts the trader typed by hand, such as "Record payment" on a customer's page. */
        suspend fun confirm(drafts: List<ConfirmableDraft>, source: JournalSource): ConfirmResult {
            val resolver = CustomerResolver(customers, ids)
            val now = clock.instant()
            val journals = mutableListOf<Journal>()
            drafts.forEachIndexed { index, item ->
                val customerId = resolver.resolve(item.draft.customerName)
                val request = JournalRequest(item.draft, customerId, item.journalId, item.idempotencyKey, now, source)
                when (val built = JournalBuilder.build(request)) {
                    is Outcome.Ok -> journals += built.value
                    is Outcome.Err -> return ConfirmResult.Invalid(index, built.error)
                }
            }
            return when (val result = ledger.write(LedgerWrite(journals, resolver.created))) {
                is WriteResult.Written ->
                    if (result.written == 0) ConfirmResult.AlreadySaved else ConfirmResult.Saved(result.written)
                is WriteResult.Rejected -> ConfirmResult.Failed(result.reason)
            }
        }
    }

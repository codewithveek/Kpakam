package app.kpakam.features.ledger

import app.kpakam.core.common.Outcome
import app.kpakam.core.common.flatMap
import app.kpakam.core.draft.Draft
import app.kpakam.core.ids.IdGenerator
import app.kpakam.core.ids.Ulid
import app.kpakam.core.ledger.InferenceSource
import app.kpakam.core.ledger.Journal
import app.kpakam.core.ledger.JournalBuilder
import app.kpakam.core.ledger.JournalRequest
import app.kpakam.core.ledger.JournalSource
import app.kpakam.core.ledger.LedgerError
import app.kpakam.core.ledger.Reversal
import app.kpakam.core.ledger.ReversalRequest
import app.kpakam.features.customers.CustomerRepository
import app.kpakam.features.customers.CustomerResolver
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** The result of an undo or a fix. */
sealed interface CorrectionResult {
    data object Done : CorrectionResult

    data object NotFound : CorrectionResult

    data class Invalid(val error: LedgerError) : CorrectionResult

    data class Failed(val reason: String) : CorrectionResult
}

/**
 * Use case: corrections by reversal (F4). "Undo" writes a reversal; "fix" writes a reversal
 * plus a new entry in one batch. The original never changes, and history shows both.
 */
@Singleton
class CorrectionUseCase
    @Inject
    constructor(
        private val ledger: LedgerRepository,
        private val customers: CustomerRepository,
        private val ids: IdGenerator,
        private val clock: Clock,
    ) {
        /** Reverses journal [id]. */
        suspend fun undo(id: Ulid): CorrectionResult {
            val original = ledger.find(id) ?: return CorrectionResult.NotFound
            return when (val reversal = reverse(original)) {
                is Outcome.Err -> CorrectionResult.Invalid(reversal.error)
                is Outcome.Ok -> save(LedgerWrite(listOf(reversal.value), emptyList()))
            }
        }

        /** Reverses journal [id] and saves [replacement] as a new entry, atomically. */
        suspend fun fix(id: Ulid, replacement: Draft): CorrectionResult {
            val original = ledger.find(id) ?: return CorrectionResult.NotFound
            val resolver = CustomerResolver(customers, ids)
            val customerId = resolver.resolve(replacement.customerName)
            val source = JournalSource(InferenceSource.MANUAL, CORRECTION_REASON, null)
            val built =
                reverse(original).flatMap { reversal ->
                    val request =
                        JournalRequest(
                            replacement,
                            customerId,
                            ids.next(),
                            "fix:${original.id}",
                            clock.instant(),
                            source,
                        )
                    JournalBuilder.build(request).flatMap { Outcome.Ok(listOf(reversal, it)) }
                }
            return when (built) {
                is Outcome.Err -> CorrectionResult.Invalid(built.error)
                is Outcome.Ok -> save(LedgerWrite(built.value, resolver.created))
            }
        }

        private suspend fun reverse(original: Journal): Outcome<Journal, LedgerError> =
            Reversal.reverse(
                ReversalRequest(
                    original = original,
                    alreadyReversed = ledger.hasReversal(original.id),
                    id = ids.next(),
                    idempotencyKey = "reversal:${original.id}",
                    occurredAt = clock.instant(),
                    source = JournalSource(InferenceSource.MANUAL, REVERSAL_REASON, null),
                ),
            )

        private suspend fun save(write: LedgerWrite): CorrectionResult =
            when (val result = ledger.write(write)) {
                is WriteResult.Written -> CorrectionResult.Done
                is WriteResult.Rejected -> CorrectionResult.Failed(result.reason)
            }

        private companion object {
            const val REVERSAL_REASON = "REVERSAL"
            const val CORRECTION_REASON = "CORRECTION"
        }
    }

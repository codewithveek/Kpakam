package app.kpakam.features.ledger

import app.kpakam.core.ids.CustomerId
import app.kpakam.core.ids.Ulid
import app.kpakam.core.ledger.Journal
import kotlinx.coroutines.flow.Flow

/** A journal plus whether it has reached the server yet (F2 "pending sync" mark). */
data class JournalRecord(val journal: Journal, val pendingSync: Boolean)

/** A customer created in the same batch as the journal that names them. */
data class NewCustomer(val id: CustomerId, val name: String, val nameKey: String)

/** One atomic write: journals, any new customers, and the cached balance increments. */
data class LedgerWrite(val journals: List<Journal>, val newCustomers: List<NewCustomer>)

/** What happened to a [LedgerWrite]. */
sealed interface WriteResult {
    /** [written] journals were saved (offline: saved locally); [skipped] already existed (idempotent no-op). */
    data class Written(val written: Int, val skipped: Int) : WriteResult

    /** The write broke an invariant and nothing was saved. This means a bug upstream. */
    data class Rejected(val reason: String) : WriteResult
}

/** Journals in Firestore. Create only: journals are never updated or deleted. */
interface LedgerRepository {
    /** Journals whose Africa/Lagos day is in [fromDay]..[toDay], newest first. */
    fun journalsBetween(fromDay: String, toDay: String): Flow<List<JournalRecord>>

    /** One customer's journals, newest first. */
    fun journalsForCustomer(customerId: CustomerId): Flow<List<JournalRecord>>

    /** Every journal, oldest first (for "Download my records"). */

    suspend fun all(): List<Journal>

    /** Finds a journal by id, from the cache first. */
    suspend fun find(id: Ulid): Journal?

    /** `true` when a journal with `reverses == id` exists. */
    suspend fun hasReversal(id: Ulid): Boolean

    /** Writes journals, new customers, and balance increments in one batch. */
    suspend fun write(write: LedgerWrite): WriteResult
}

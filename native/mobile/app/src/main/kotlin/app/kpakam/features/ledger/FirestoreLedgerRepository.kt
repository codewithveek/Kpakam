package app.kpakam.features.ledger

import android.util.Log
import app.kpakam.core.common.Outcome
import app.kpakam.core.ids.CustomerId
import app.kpakam.core.ids.Ulid
import app.kpakam.core.ledger.Balances
import app.kpakam.core.ledger.Invariants
import app.kpakam.core.ledger.Journal
import app.kpakam.core.ledger.JournalFields
import app.kpakam.features.customers.CustomerFields
import app.kpakam.platform.firebase.UserPaths
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Firestore `users/{uid}/journals`, with offline persistence. */
@Singleton
class FirestoreLedgerRepository
    @Inject
    constructor(
        private val db: FirebaseFirestore,
        private val paths: UserPaths,
    ) : LedgerRepository {
        override fun journalsBetween(fromDay: String, toDay: String): Flow<List<JournalRecord>> =
            paths.forUser { user ->
                user
                    .collection(UserPaths.JOURNALS)
                    .whereGreaterThanOrEqualTo(JournalFields.DAY, fromDay)
                    .whereLessThanOrEqualTo(JournalFields.DAY, toDay)
                    .orderBy(JournalFields.DAY)
                    .orderBy(JournalFields.OCCURRED_AT, Query.Direction.DESCENDING)
                    .records()
            }

        override fun journalsForCustomer(customerId: CustomerId): Flow<List<JournalRecord>> =
            paths.forUser { user ->
                user
                    .collection(UserPaths.JOURNALS)
                    .whereEqualTo(JournalFields.CUSTOMER_ID, customerId.value)
                    .orderBy(JournalFields.OCCURRED_AT, Query.Direction.DESCENDING)
                    .records()
            }

        override suspend fun all(): List<Journal> {
            val query = paths.journals().orderBy(JournalFields.OCCURRED_AT)

            val snapshot = getOrNull { query.get().await() } ?: getOrNull { query.get(Source.CACHE).await() }

            return snapshot?.documents.orEmpty().mapNotNull { doc -> doc.data?.let(JournalMapper::fromMap) }
        }

        override suspend fun find(id: Ulid): Journal? {
            val ref = paths.journals().document(id.value)
            val cached = getOrNull { ref.get(Source.CACHE).await() }
            val snapshot = if (cached?.exists() == true) cached else getOrNull { ref.get().await() }
            return snapshot?.data?.let(JournalMapper::fromMap)
        }

        override suspend fun hasReversal(id: Ulid): Boolean {
            val query = paths.journals().whereEqualTo(JournalFields.REVERSES, id.value).limit(1)
            if (getOrNull { query.get(Source.CACHE).await() }?.isEmpty == false) return true
            return getOrNull { query.get().await() }?.isEmpty == false
        }

        override suspend fun write(write: LedgerWrite): WriteResult {
            write.journals.forEach { journal ->
                val check = Invariants.check(journal.lines)
                if (check is Outcome.Err) return WriteResult.Rejected(check.error.code)
            }
            val journals = paths.journals()
            val fresh = write.journals.filterNot { existsInCache(journals.document(it.id.value)) }
            if (fresh.isEmpty()) return WriteResult.Written(written = 0, skipped = write.journals.size)
            commit(fresh, write.newCustomers, journals, paths.customers())
            return WriteResult.Written(written = fresh.size, skipped = write.journals.size - fresh.size)
        }

        private fun commit(
            journals: List<Journal>,
            newCustomers: List<NewCustomer>,
            journalRefs: CollectionReference,
            customerRefs: CollectionReference,
        ) {
            val deltas = Balances.allCustomerBalances(journals)
            val created = newCustomers.associateBy { it.id }
            val batch = db.batch()
            journals.forEach { batch.set(journalRefs.document(it.id.value), JournalMapper.toMap(it)) }
            created.values.forEach { customer ->
                val delta = deltas[customer.id]?.value ?: 0L
                batch.set(customerRefs.document(customer.id.value), CustomerFields.newCustomer(customer, delta))
            }
            deltas.filterKeys { it !in created }.forEach { (id, delta) ->
                batch.update(
                    customerRefs.document(id.value),
                    mapOf(
                        CustomerFields.BALANCE_KOBO to FieldValue.increment(delta.value),
                        CustomerFields.UPDATED_AT to FieldValue.serverTimestamp(),
                    ),
                )
            }
            // Not awaited: offline, the task only completes when the server confirms.
            // The local cache has the write at once, so the UI updates with a pending-sync mark.
            batch.commit().addOnFailureListener { Log.e(TAG, "Journal batch rejected by the server", it) }
        }

        private suspend fun existsInCache(ref: DocumentReference): Boolean =
            getOrNull { ref.get(Source.CACHE).await() }?.exists() == true

        private fun Query.records(): Flow<List<JournalRecord>> =
            snapshots(MetadataChanges.INCLUDE).map { it.toRecords() }

        private fun QuerySnapshot.toRecords(): List<JournalRecord> =
            documents.mapNotNull { doc ->
                doc.data?.let(JournalMapper::fromMap)?.let { JournalRecord(it, doc.metadata.hasPendingWrites()) }
            }

        private suspend fun <T> getOrNull(block: suspend () -> T): T? =
            try {
                block()
            } catch (e: FirebaseFirestoreException) {
                null
            }

        private companion object {
            const val TAG = "Ledger"
        }
    }

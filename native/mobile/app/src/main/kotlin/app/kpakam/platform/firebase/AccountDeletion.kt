package app.kpakam.platform.firebase

import android.util.Log
import app.kpakam.inference.queue.AiJobQueue
import com.google.firebase.FirebaseException
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Deletes every record of the trader (PRD privacy). Irreversible. */
fun interface AccountDeleter {
    /** Returns `true` when every record and the account were deleted. */
    suspend fun deleteEverything(): Boolean
}

/**
 * Firestore adapter for [AccountDeleter], in the order the security rules require: mark the profile
 * (`deletionRequestedAt`), delete the subcollections in batches, delete the profile, cancel waiting
 * notes on the phone, and finally delete the sign-in account.
 */
@Singleton
class FirestoreAccountDeleter
    @Inject
    constructor(
        private val db: FirebaseFirestore,
        private val paths: UserPaths,
        private val auth: AuthGateway,
        private val queue: AiJobQueue,
    ) : AccountDeleter {
        override suspend fun deleteEverything(): Boolean =
            try {
                val user = paths.user()
                user
                    .update(
                        mapOf(
                            "deletionRequestedAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ),
                    ).await()
                SUBCOLLECTIONS.forEach { name -> deleteAll(user.collection(name)) }
                user.delete().await()
                queue.open.first().forEach { queue.cancel(it.id) }
                auth.deleteAndRestart()
            } catch (e: FirebaseException) {
                Log.w(TAG, "Account deletion stopped", e)
                false
            }

        private suspend fun deleteAll(collection: CollectionReference) {
            val docs = collection.get().await().documents
            docs.chunked(BATCH_SIZE).forEach { chunk ->
                db
                    .batch()
                    .apply { chunk.forEach { delete(it.reference) } }
                    .commit()
                    .await()
            }
        }

        private companion object {
            const val TAG = "AccountDeletion"
            const val BATCH_SIZE = 400
            val SUBCOLLECTIONS = listOf(UserPaths.JOURNALS, UserPaths.CUSTOMERS, UserPaths.AI_EVENTS)
        }
    }

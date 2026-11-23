package app.kpakam.features.customers

import app.kpakam.core.ids.CustomerId
import app.kpakam.core.money.Kobo
import app.kpakam.platform.firebase.UserPaths
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Firestore `users/{uid}/customers`. */
@Singleton
class FirestoreCustomerRepository
    @Inject
    constructor(
        private val paths: UserPaths,
    ) : CustomerRepository {
        override val customers: Flow<List<Customer>> =
            paths.forUser { user ->
                user
                    .collection(UserPaths.CUSTOMERS)
                    .orderBy(CustomerFields.BALANCE_KOBO, Query.Direction.DESCENDING)
                    .snapshots(MetadataChanges.INCLUDE)
                    .map { snapshot -> snapshot.documents.mapNotNull(::toCustomer) }
            }

        override fun customer(id: CustomerId): Flow<Customer?> =
            paths.forUser { user ->
                user
                    .collection(UserPaths.CUSTOMERS)
                    .document(id.value)
                    .snapshots(MetadataChanges.INCLUDE)
                    .map(::toCustomer)
            }

        override suspend fun findByNameKey(nameKey: String): Customer? {
            val query = paths.customers().whereEqualTo(CustomerFields.NAME_KEY, nameKey).limit(1)
            val cached = getOrNull { query.get(Source.CACHE).await() }?.documents?.firstOrNull()
            return (cached ?: getOrNull { query.get().await() }?.documents?.firstOrNull())?.let(::toCustomer)
        }

        private fun toCustomer(doc: DocumentSnapshot): Customer? {
            if (!doc.exists()) return null
            return Customer(
                id = doc.getString(CustomerFields.ID)?.let(CustomerId::parse) ?: return null,
                name = doc.getString(CustomerFields.NAME) ?: return null,
                nameKey = doc.getString(CustomerFields.NAME_KEY) ?: return null,
                phone = doc.getString(CustomerFields.PHONE),
                cachedBalance = Kobo(doc.getLong(CustomerFields.BALANCE_KOBO) ?: 0L),
                pendingSync = doc.metadata.hasPendingWrites(),
            )
        }

        private suspend fun <T> getOrNull(block: suspend () -> T): T? =
            try {
                block()
            } catch (e: FirebaseFirestoreException) {
                null
            }
    }

package app.kpakam.features.customers

import app.kpakam.core.ids.CustomerId
import app.kpakam.core.money.Kobo
import app.kpakam.features.ledger.NewCustomer
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.Flow

/** A customer. [cachedBalance] is the `balanceKobo` cache; the detail screen shows the derived balance. */
data class Customer(
    val id: CustomerId,
    val name: String,
    val nameKey: String,
    val phone: String?,
    val cachedBalance: Kobo,
    val pendingSync: Boolean,
)

/** Customers in Firestore. */
interface CustomerRepository {
    /** All customers, largest debt first. */
    val customers: Flow<List<Customer>>

    /** One customer, live. */
    fun customer(id: CustomerId): Flow<Customer?>

    /** Finds a customer by match key (lower-case, trimmed, single spaces). */
    suspend fun findByNameKey(nameKey: String): Customer?
}

/** Stored field names of a customer document. They match the PWA exactly. */
object CustomerFields {
    const val ID = "id"
    const val NAME = "name"
    const val NAME_KEY = "nameKey"
    const val PHONE = "phone"
    const val BALANCE_KOBO = "balanceKobo"
    const val CREATED_AT = "createdAt"
    const val UPDATED_AT = "updatedAt"
    const val SCHEMA_VERSION = "schemaVersion"

    /** The document for a new customer. The balance uses `increment`, like every balance change. */
    fun newCustomer(customer: NewCustomer, balanceDelta: Long): Map<String, Any?> =
        mapOf(
            ID to customer.id.value,
            NAME to customer.name,
            NAME_KEY to customer.nameKey,
            PHONE to null,
            BALANCE_KOBO to FieldValue.increment(balanceDelta),
            CREATED_AT to FieldValue.serverTimestamp(),
            UPDATED_AT to FieldValue.serverTimestamp(),
            SCHEMA_VERSION to 1,
        )
}

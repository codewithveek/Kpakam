package app.kpakam.features.customers

import app.kpakam.core.customers.cleanDisplayName
import app.kpakam.core.customers.nameKey
import app.kpakam.core.ids.CustomerId
import app.kpakam.core.ids.IdGenerator
import app.kpakam.features.ledger.NewCustomer

/**
 * Matches a draft's `customerName` to an existing customer by name key, or plans a new
 * customer (ULID id) to create in the same batch as the journal. One resolver per save,
 * so two drafts that name the same new customer share one id.
 */
class CustomerResolver(
    private val customers: CustomerRepository,
    private val ids: IdGenerator,
) {
    private val planned = linkedMapOf<String, NewCustomer>()

    /** New customers to create with the journals. */
    val created: List<NewCustomer> get() = planned.values.toList()

    /** Returns the customer id for [name], or `null` when the name is blank. */
    suspend fun resolve(name: String?): CustomerId? {
        if (name.isNullOrBlank()) return null
        val key = nameKey(name)
        planned[key]?.let { return it.id }
        customers.findByNameKey(key)?.let { return it.id }
        val customer = NewCustomer(CustomerId.of(ids.next()), cleanDisplayName(name), key)
        planned[key] = customer
        return customer.id
    }
}

package app.kpakam.features.settings

import app.kpakam.core.ledger.JournalJson
import app.kpakam.core.time.LagosTime
import app.kpakam.features.customers.CustomerRepository
import app.kpakam.features.ledger.LedgerRepository
import app.kpakam.platform.files.DocumentWriter
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Use case: "Download my records". Writes every journal and customer as one JSON file (PRD privacy). */
@Singleton
class ExportRecordsUseCase
    @Inject
    constructor(
        private val ledger: LedgerRepository,
        private val customers: CustomerRepository,
        private val writer: DocumentWriter,
        private val clock: Clock,
    ) {
        /** Writes the records to the document at [uri]. Returns `false` when the file could not be written. */
        suspend fun export(uri: String): Boolean {
            val people =
                customers.customers.first().map {
                    JsonObject(
                        mapOf(
                            "id" to JsonPrimitive(it.id.value),
                            "name" to JsonPrimitive(it.name),
                            "phone" to (it.phone?.let(::JsonPrimitive) ?: JsonNull),
                            "balanceKobo" to JsonPrimitive(it.cachedBalance.value),
                        ),
                    )
                }
            val document =
                JsonObject(
                    mapOf(
                        "exportedAt" to JsonPrimitive(LagosTime.occurredAt(clock.instant())),
                        "journals" to JsonArray(ledger.all().map(JournalJson::toJson)),
                        "customers" to JsonArray(people),
                    ),
                )
            return writer.write(uri, PRETTY.encodeToString(JsonObject.serializer(), document))
        }

        private companion object {
            val PRETTY = Json { prettyPrint = true }
        }
    }

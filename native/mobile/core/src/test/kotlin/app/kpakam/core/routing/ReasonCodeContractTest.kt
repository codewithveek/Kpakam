package app.kpakam.core.routing

import app.kpakam.core.ContractFiles
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The reason and queue codes in routing.md match the Kotlin enums exactly. */
class ReasonCodeContractTest {
    private val routingDoc = ContractFiles.text("routing.md")

    @Test
    fun `every reason code in the routing table exists, and no more`() {
        val table = routingDoc.substringAfter("## Reason codes").substringBefore("Queue reasons:")
        val row = Regex("^\\| `([A-Z_]+)` *\\|", RegexOption.MULTILINE)
        val codes = row.findAll(table).map { it.groupValues[1] }.toSet()
        assertEquals(codes, ReasonCode.entries.map { it.name }.toSet())
    }

    @Test
    fun `every queue reason in routing md exists, and no more`() {
        val text = routingDoc.substringAfter("Queue reasons:").substringBefore("## ")
        val codes = Regex("`([A-Z_]+)`").findAll(text).map { it.groupValues[1] }.toSet()
        assertEquals(codes, QueueReason.entries.map { it.name }.toSet())
    }
}

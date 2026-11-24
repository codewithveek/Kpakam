package app.kpakam.features.ledger

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import app.kpakam.core.ids.CustomerId
import app.kpakam.core.ids.Ulid
import app.kpakam.shared.ui.JournalCard
import app.kpakam.shared.ui.PageGutter

/** Entries as white cards in a lazy list, each with Undo and Fix. [names] maps customers to names. */
fun LazyListScope.entryCards(
    records: List<JournalRecord>,
    names: Map<CustomerId, String>,
    reversed: Set<Ulid>,
    corrections: CorrectionsViewModel,
) {
    items(records, key = { it.journal.id.value }) { record ->
        val name = record.journal.customerId?.let { names[it] }
        JournalCard(
            journal = record.journal,
            pendingSync = record.pendingSync,
            reversed = record.journal.id in reversed,
            customerName = name,
            actions = rememberEntryActions(corrections, record.journal, name),
            modifier = Modifier.padding(horizontal = PageGutter),
        )
    }
}

package app.kpakam.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kpakam.R
import app.kpakam.core.ledger.Journal
import app.kpakam.core.ledger.JournalKind
import app.kpakam.core.money.Kobo
import app.kpakam.core.money.sumKobo
import app.kpakam.shared.time.LagosDates

/** The amount a trader thinks of for an entry: the sum of its positive lines. */
fun Journal.displayAmount(): Kobo = lines.map { it.amount }.filter { it.value > 0 }.sumKobo()

/** One journal for lists: kind, description, time, amount, and sync state. */
@Composable
fun JournalRow(journal: Journal, pendingSync: Boolean, customerName: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(journalKindLabel(journal.kind)), style = MaterialTheme.typography.labelLarge)
                Text(journal.description, style = MaterialTheme.typography.bodyLarge)
                customerName?.let {
                    Text(
                        stringResource(R.string.draft_customer, it),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (journal.kind == JournalKind.REVERSAL) {
                    Text(stringResource(R.string.history_reverses), style = MaterialTheme.typography.bodyMedium)
                }
            }
            AmountText(journal.displayAmount())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(LagosDates.time(journal.occurredAt), style = MaterialTheme.typography.bodyMedium)
            SyncMark(pendingSync)
        }
    }
}

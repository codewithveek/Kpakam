package app.kpakam.features.customers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kpakam.R
import app.kpakam.core.money.Kobo
import app.kpakam.shared.money.NairaFormat
import app.kpakam.shared.ui.AmountText
import app.kpakam.shared.ui.JournalRow
import app.kpakam.shared.ui.MinTouchTarget
import app.kpakam.shared.ui.SectionHeader
import app.kpakam.shared.ui.SyncMark

/** "Who owes you": customers with what they owe, largest first. */
@Composable
fun CustomersScreen(onOpen: (String) -> Unit, viewModel: CustomersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item { SectionHeader(stringResource(R.string.owed_title)) }
        item { Text(stringResource(R.string.owed_total, NairaFormat.format(state.totalOwed))) }
        if (state.customers.isEmpty()) item { Text(stringResource(R.string.owed_empty)) }
        items(state.customers, key = { it.id.value }) { customer ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = MinTouchTarget)
                        .clickable(role = Role.Button) { onOpen(customer.id.value) }
                        .padding(vertical = 12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(customer.name, style = MaterialTheme.typography.titleLarge)
                    Text(balanceText(customer.cachedBalance))
                }
                if (customer.pendingSync) SyncMark(pending = true)
            }
            HorizontalDivider()
        }
    }
}

/** One customer: derived balance and their entries. */
@Composable
fun CustomerDetailScreen(onBack: () -> Unit, viewModel: CustomerDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                SectionHeader(state.customer?.name.orEmpty())
            }
        }
        item {
            Text(balanceText(state.balance), style = MaterialTheme.typography.bodyLarge)
            AmountText(state.balance, large = true)
        }
        item { SectionHeader(stringResource(R.string.customer_history)) }
        items(state.entries, key = { it.journal.id.value }) { record ->
            JournalRow(record.journal, record.pendingSync, customerName = null)
            HorizontalDivider()
        }
    }
}

@Composable
private fun balanceText(balance: Kobo): String =
    when {
        balance.value > 0 -> stringResource(R.string.owed_balance_owes, NairaFormat.format(balance))
        balance.value < 0 -> stringResource(R.string.owed_balance_credit, NairaFormat.format(-balance))
        else -> stringResource(R.string.owed_balance_clear)
    }

package app.kpakam.features.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kpakam.R
import app.kpakam.core.money.Kobo
import app.kpakam.shared.ui.AmountText
import app.kpakam.shared.ui.OfflineBanner
import app.kpakam.shared.ui.SectionHeader
import app.kpakam.shared.ui.StatusChip
import app.kpakam.shared.ui.SyncMark

/** Today: sales, expenses, profit, and debt collected, in naira. */
@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(stringResource(R.string.today_title))
        if (!state.online) OfflineBanner()
        if (state.pendingSync > 0) SyncMark(pending = true)
        if (state.entries == 0) Text(stringResource(R.string.today_empty))
        TotalCard(R.string.today_sales, state.totals.sales)
        TotalCard(R.string.today_expenses, state.totals.expenses)
        TotalCard(R.string.today_profit, state.totals.profit)
        TotalCard(R.string.today_collected, state.totals.debtCollected)
        StatusChip(Icons.Filled.PhoneAndroid, stringResource(R.string.today_avoided, state.cloudCallsAvoided))
        Text(stringResource(R.string.today_avoided_help), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TotalCard(label: Int, amount: Kobo) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
            AmountText(amount, large = true)
        }
    }
}

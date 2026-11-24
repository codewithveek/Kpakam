package app.kpakam.features.customers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kpakam.R
import app.kpakam.core.money.Kobo
import app.kpakam.shared.money.NairaFormat
import app.kpakam.shared.ui.IconTile
import app.kpakam.shared.ui.InitialsTile
import app.kpakam.shared.ui.InkCard
import app.kpakam.shared.ui.KCard
import app.kpakam.shared.ui.Kpakam
import app.kpakam.shared.ui.KpakamType
import app.kpakam.shared.ui.PageGutter
import app.kpakam.shared.ui.PageHeader
import app.kpakam.shared.ui.Radii
import app.kpakam.shared.ui.RowContent
import app.kpakam.shared.ui.SubtitleText
import app.kpakam.shared.ui.rowClick

/** "Who owes you": the total, then every customer with what they owe, largest first. */
@Composable
fun CustomersScreen(
    onOpen: (String) -> Unit,
    contentPadding: PaddingValues,
    viewModel: CustomersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { PageHeader(stringResource(R.string.owed_title), Modifier.padding(horizontal = PageGutter)) }
        item { OwedCard(state.totalOwed, state.debtors, Modifier.padding(horizontal = PageGutter, vertical = 12.dp)) }
        if (!state.loading && state.customers.isEmpty()) item { EmptyDebts() }
        items(state.customers, key = { it.id.value }) { customer ->
            CustomerRow(customer, Modifier.padding(horizontal = PageGutter)) { onOpen(customer.id.value) }
        }
    }
}

@Composable
private fun OwedCard(total: Kobo, debtors: Int, modifier: Modifier) {
    InkCard(modifier) {
        Text(
            stringResource(R.string.owed_total),
            style = KpakamType.body,
            color = Kpakam.colors.onInkMuted,
            modifier = Modifier.semantics { heading() },
        )
        Text(NairaFormat.format(total), style = KpakamType.bigAmount, modifier = Modifier.padding(top = 12.dp))
        Text(
            pluralStringResource(R.plurals.owed_from_customers, debtors, debtors),
            style = KpakamType.body,
            color = Kpakam.colors.onInkMuted,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun EmptyDebts() {
    KCard(modifier = Modifier.padding(horizontal = PageGutter), padding = PaddingValues(32.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconTile(Icons.Outlined.People)
            Text(
                stringResource(R.string.owed_empty),
                style = KpakamType.body,
                color = Kpakam.colors.muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CustomerRow(customer: Customer, modifier: Modifier, onClick: () -> Unit) {
    val colors = Kpakam.colors
    val balance = customer.cachedBalance
    val tone = if (balance.value > 0) colors.credit else colors.muted
    Surface(shape = Radii.row, color = colors.surface, modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.rowClick(onClick).padding(end = 16.dp),
        ) {
            RowContent(
                title = customer.name,
                leading = { InitialsTile(customer.name) },
                subtitle = { SubtitleText(balanceLabel(balance), tone) },
                trailing = { if (balance.value != 0L) BalanceAmount(balance) },
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                null,
                tint = colors.muted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun BalanceAmount(balance: Kobo) {
    Text(
        NairaFormat.format(Kobo(kotlin.math.abs(balance.value))),
        style = KpakamType.listAmount,
        color = if (balance.value > 0) Kpakam.colors.text else Kpakam.colors.muted,
    )
}

/** "owes you", "All paid", or "You owe them". */
@Composable
fun balanceLabel(balance: Kobo): String =
    stringResource(
        when {
            balance.value > 0 -> R.string.owed_owes_you
            balance.value < 0 -> R.string.owed_in_credit
            else -> R.string.owed_balance_clear
        },
    )

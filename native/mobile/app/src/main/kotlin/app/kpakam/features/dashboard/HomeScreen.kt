package app.kpakam.features.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kpakam.R
import app.kpakam.core.ledger.DayTotals
import app.kpakam.core.money.Kobo
import app.kpakam.features.ledger.CorrectionDialogs
import app.kpakam.features.ledger.CorrectionsViewModel
import app.kpakam.features.ledger.EmptyCard
import app.kpakam.features.ledger.entryCards
import app.kpakam.features.quickentry.DraftEditing
import app.kpakam.features.quickentry.DraftList
import app.kpakam.features.quickentry.EntryMessageCard
import app.kpakam.features.quickentry.JobCard
import app.kpakam.features.quickentry.QuickEntryPanel
import app.kpakam.features.quickentry.QuickEntryViewModel
import app.kpakam.shared.money.NairaFormat
import app.kpakam.shared.time.LagosDates
import app.kpakam.shared.ui.InkCard
import app.kpakam.shared.ui.Kpakam
import app.kpakam.shared.ui.KpakamType
import app.kpakam.shared.ui.PageGutter
import app.kpakam.shared.ui.PageHeader
import app.kpakam.shared.ui.PillButton
import app.kpakam.shared.ui.PillStyle
import app.kpakam.shared.ui.SectionHeader

private const val NOON = 12
private const val EVENING = 17

/** The main screen: today's numbers, quick entry, drafts to check, latest entries. */
@Composable
fun HomeScreen(
    entry: QuickEntryViewModel,
    onSpeak: () -> Unit,
    onSeeAll: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: DashboardViewModel = hiltViewModel(),
    corrections: CorrectionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val input by entry.state.collectAsStateWithLifecycle()
    val gutter = Modifier.padding(horizontal = PageGutter)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            PageHeader(
                greeting(state.hour),
                gutter.padding(bottom = 12.dp),
                state.today
                    .ifEmpty {
                        null
                    }?.let(LagosDates::longDay),
            )
        }
        item { ProfitCard(state.totals, gutter.padding(bottom = 12.dp)) }
        item { QuickEntryPanel(input, entry, onSpeak, gutter) }
        input.message?.let { message -> item { EntryMessageCard(message, entry::dismissMessage, gutter) } }
        if (input.drafts.isNotEmpty()) item { DraftList(input, entry, gutter.padding(top = 4.dp)) }
        items(input.jobs, key = { it.id }) { job -> JobCard(job, entry, gutter) }
        item {
            SectionHeader(stringResource(R.string.today_recent), gutter.padding(top = 8.dp)) {
                if (state.entries > state.recent.size) {
                    PillButton(stringResource(R.string.today_see_all), onSeeAll, style = PillStyle.Secondary)
                }
            }
        }
        if (!state.loading && state.recent.isEmpty()) item { EmptyCard(stringResource(R.string.today_empty)) }
        entryCards(state.recent, state.names, state.reversed, corrections)
    }
    DraftEditing(input, entry)
    CorrectionDialogs(corrections)
}

@Composable
private fun greeting(hour: Int): String =
    stringResource(
        when {
            hour < NOON -> R.string.greeting_morning
            hour < EVENING -> R.string.greeting_afternoon
            else -> R.string.greeting_evening
        },
    )

/** F5: the one black card. Profit is the single big number; sales, expenses, and paid back sit below. */
@Composable
private fun ProfitCard(totals: DayTotals, modifier: Modifier) {
    val colors = Kpakam.colors
    InkCard(modifier) {
        Text(
            stringResource(R.string.today_profit_today),
            style = KpakamType.body,
            fontWeight = FontWeight.Medium,
            color = colors.onInkMuted,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            NairaFormat.format(totals.profit),
            style = KpakamType.heroAmount,
            color = colors.onInk,
            maxLines = 1,
            modifier = Modifier.padding(top = 12.dp),
        )
        HorizontalDivider(
            color = colors.inkLine,
            thickness = 1.dp,
            modifier = Modifier.padding(top = 24.dp, bottom = 20.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat(stringResource(R.string.today_sales), totals.sales, Modifier.weight(1f))
            Stat(stringResource(R.string.today_expenses), totals.expenses, Modifier.weight(1f))
            Stat(stringResource(R.string.today_paid_back), totals.debtCollected, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(label: String, amount: Kobo, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = KpakamType.small,
            fontWeight = FontWeight.Medium,
            color = Kpakam.colors.onInkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            NairaFormat.format(amount),
            style = KpakamType.listAmount,
            color = Kpakam.colors.onInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

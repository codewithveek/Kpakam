package app.kpakam.features.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kpakam.R
import app.kpakam.core.money.Kobo
import app.kpakam.shared.time.LagosDates
import app.kpakam.shared.ui.KCard
import app.kpakam.shared.ui.Kpakam
import app.kpakam.shared.ui.KpakamType
import app.kpakam.shared.ui.MoneyText
import app.kpakam.shared.ui.MoneyTone
import app.kpakam.shared.ui.PageGutter
import app.kpakam.shared.ui.PageHeader
import app.kpakam.shared.ui.Radii

/** History: one day at a time, newest first. Tap an entry to Undo or Fix it. */
@Composable
fun HistoryScreen(
    contentPadding: PaddingValues,
    viewModel: HistoryViewModel = hiltViewModel(),
    corrections: CorrectionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            PageHeader(
                stringResource(R.string.history_title),
                Modifier.padding(horizontal = PageGutter),
                LagosDates.longDay(state.selected),
            )
        }
        item {
            DayStrip(
                state.days,
                state.today,
                state.selected,
                viewModel::select,
                Modifier.padding(vertical = 12.dp),
            )
        }
        if (state.records.isNotEmpty()) {
            item { DayStats(state.totals.sales, state.totals.expenses) }
        }
        if (!state.loading && state.records.isEmpty()) {
            item { EmptyCard(stringResource(R.string.history_empty)) }
        }
        entryCards(state.records, state.names, state.reversed, corrections)
    }
    CorrectionDialogs(corrections)
}

/** The last two weeks as a horizontal strip of round day chips (radio semantics). */
@Composable
private fun DayStrip(
    days: List<String>,
    today: String,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier,
) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (days.size - 1).coerceAtLeast(0))
    LaunchedEffect(selected) {
        val index = days.indexOf(selected)
        if (index >= 0) list.animateScrollToItem(maxOf(0, index - 2))
    }
    LazyRow(
        state = list,
        contentPadding = PaddingValues(horizontal = PageGutter),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().selectableGroup(),
    ) {
        items(days, key = { it }) { day -> DayChip(day, day == today, day == selected) { onSelect(day) } }
    }
}

@Composable
private fun DayChip(day: String, isToday: Boolean, active: Boolean, onClick: () -> Unit) {
    val colors = Kpakam.colors
    val label = if (isToday) stringResource(R.string.history_today) else LagosDates.weekday(day)
    val full = if (isToday) label else LagosDates.longDay(day)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier =
            Modifier
                .width(58.dp)
                .heightIn(min = 76.dp)
                .clip(CircleShape)
                .background(if (active) colors.ink else colors.surface)
                .selectable(selected = active, role = Role.RadioButton, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = full },
    ) {
        Text(
            label,
            style = KpakamType.label,
            fontWeight = FontWeight.SemiBold,
            color = if (active) colors.onInkMuted else colors.muted,
        )
        Text(
            LagosDates.dayOfMonth(day),
            style = KpakamType.dayNumber,
            color = if (active) colors.onInk else colors.text,
        )
    }
}

@Composable
private fun DayStats(sales: Kobo, expenses: Kobo) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = PageGutter)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        StatCard(stringResource(R.string.today_sales), sales, MoneyTone.Income, Modifier.weight(1f))
        StatCard(stringResource(R.string.today_expenses), expenses, MoneyTone.Expense, Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(label: String, amount: Kobo, tone: MoneyTone, modifier: Modifier) {
    KCard(modifier = modifier, shape = Radii.row, padding = PaddingValues(16.dp)) {
        Text(label, style = KpakamType.small, fontWeight = FontWeight.Medium, color = Kpakam.colors.muted)
        MoneyText(amount, tone, KpakamType.statAmount, Modifier.padding(top = 4.dp))
    }
}

/** A quiet message in a white card, for empty lists. */
@Composable
fun EmptyCard(text: String, modifier: Modifier = Modifier) {
    KCard(modifier = modifier.padding(horizontal = PageGutter), shape = Radii.row, padding = PaddingValues(24.dp)) {
        Text(
            text,
            style = KpakamType.body,
            color = Kpakam.colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

package app.kpakam.features.quickentry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kpakam.R
import app.kpakam.core.draft.DraftKind
import app.kpakam.core.ledger.JournalBuilder
import app.kpakam.core.ledger.JournalKind
import app.kpakam.core.money.Kobo
import app.kpakam.core.routing.Confidence
import app.kpakam.shared.money.NairaFormat
import app.kpakam.shared.ui.InferenceBadge
import app.kpakam.shared.ui.MinTouchTarget
import app.kpakam.shared.ui.SectionHeader
import app.kpakam.shared.ui.StatusChip
import app.kpakam.shared.ui.draftKindLabel
import app.kpakam.shared.ui.ledgerErrorLabel

/** The drafts to check before saving (F1), with the inference badge (F6). */
@Composable
fun DraftList(state: QuickEntryUiState, viewModel: QuickEntryViewModel) {
    val speech = state.drafts.map { draftSpeech(it) }.joinToString(" ")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.drafts_title))
        state.origin?.let { InferenceBadge(it.source, it.reason) }
        val lowRoute = state.origin?.confidence == Confidence.LOW
        state.drafts.forEachIndexed { index, item ->
            DraftCard(
                item,
                lowRoute,
                onEdit = { viewModel.startEdit(index) },
                onRemove = { viewModel.removeDraft(index) },
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = viewModel::saveAll,
                enabled = !state.busy,
                modifier = Modifier.heightIn(min = MinTouchTarget),
            ) {
                Text(
                    stringResource(
                        if (state.phase ==
                            EntryPhase.Saving
                        ) {
                            R.string.drafts_saving
                        } else {
                            R.string.drafts_save
                        },
                    ),
                )
            }
            OutlinedButton(
                onClick = { viewModel.readAloud(speech) },
                modifier = Modifier.heightIn(min = MinTouchTarget),
            ) {
                Text(stringResource(R.string.drafts_read_aloud))
            }
            TextButton(onClick = viewModel::discardDrafts, modifier = Modifier.heightIn(min = MinTouchTarget)) {
                Text(stringResource(R.string.drafts_discard))
            }
        }
    }
}

@Composable
private fun DraftCard(item: DraftUi, lowRoute: Boolean, onEdit: () -> Unit, onRemove: () -> Unit) {
    val draft = item.draft
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(draftKindLabel(draft.kind)), style = MaterialTheme.typography.labelLarge)
                Text(
                    NairaFormat.formatNaira(draft.totalNaira),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(JournalBuilder.describe(draft, journalKindOf(draft.kind)), style = MaterialTheme.typography.bodyLarge)
            draft.customerName?.let { Text(stringResource(R.string.draft_customer, it)) }
            if (draft.kind == DraftKind.CREDIT_SALE) CreditLines(draft.totalNaira, draft.paidNaira ?: 0.0)
            if (lowRoute || draft.confidence < QuickEntryUiState.LOW_CONFIDENCE) {
                StatusChip(Icons.Filled.WarningAmber, stringResource(R.string.draft_low_confidence))
            }
            item.error?.let { StatusChip(Icons.Filled.ErrorOutline, stringResource(ledgerErrorLabel(it))) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = MinTouchTarget)) {
                    Text(stringResource(R.string.draft_edit))
                }
                TextButton(onClick = onRemove, modifier = Modifier.heightIn(min = MinTouchTarget)) {
                    Text(stringResource(R.string.draft_remove))
                }
            }
        }
    }
}

@Composable
private fun CreditLines(totalNaira: Double, paidNaira: Double) {
    val total = Kobo.fromNaira(totalNaira)
    val paid = Kobo.fromNaira(paidNaira)
    Text(stringResource(R.string.draft_paid_now, NairaFormat.format(paid)))
    if (paid < total) Text(stringResource(R.string.draft_owes, NairaFormat.format(total - paid)))
}

/** One sentence per draft for text-to-speech: "Sale, 3 bag rice, 45,000 naira." */
@Composable
private fun draftSpeech(item: DraftUi): String {
    val draft = item.draft
    return stringResource(
        R.string.draft_speech,
        stringResource(draftKindLabel(draft.kind)),
        JournalBuilder.describe(draft, journalKindOf(draft.kind)),
        NairaFormat.plain(Kobo.fromNaira(draft.totalNaira)),
    )
}

private fun journalKindOf(kind: DraftKind): JournalKind =
    when (kind) {
        DraftKind.SALE -> JournalKind.SALE
        DraftKind.CREDIT_SALE -> JournalKind.CREDIT_SALE
        DraftKind.REPAYMENT -> JournalKind.REPAYMENT
        DraftKind.EXPENSE -> JournalKind.EXPENSE
    }

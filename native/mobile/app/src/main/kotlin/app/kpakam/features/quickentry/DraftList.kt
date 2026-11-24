package app.kpakam.features.quickentry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.kpakam.R
import app.kpakam.core.draft.Draft
import app.kpakam.core.draft.DraftKind
import app.kpakam.core.ledger.JournalBuilder
import app.kpakam.core.ledger.JournalKind
import app.kpakam.core.money.Kobo
import app.kpakam.core.routing.Confidence
import app.kpakam.shared.money.NairaFormat
import app.kpakam.shared.ui.DraftEditorDialog
import app.kpakam.shared.ui.InferenceBadge
import app.kpakam.shared.ui.KCard
import app.kpakam.shared.ui.KindTile
import app.kpakam.shared.ui.Kpakam
import app.kpakam.shared.ui.KpakamType
import app.kpakam.shared.ui.PillButton
import app.kpakam.shared.ui.PillStyle
import app.kpakam.shared.ui.RoundIconButton
import app.kpakam.shared.ui.draftKindLabel
import app.kpakam.shared.ui.ledgerErrorLabel

/** The editor for the draft the trader tapped Edit on. */
@Composable
fun DraftEditing(state: QuickEntryUiState, viewModel: QuickEntryViewModel) {
    val index = state.editingIndex ?: return
    val item = state.drafts.getOrNull(index) ?: return
    DraftEditorDialog(item.draft, onDone = { viewModel.saveEdit(index, it) }, onDismiss = viewModel::cancelEdit)
}

/** The drafts to check before saving (F1), with where they were read (F6). */
@Composable
fun DraftList(state: QuickEntryUiState, viewModel: QuickEntryViewModel, modifier: Modifier = Modifier) {
    val speech = state.drafts.map { draftSpeech(it.draft) }.joinToString(" ")
    KCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(
                    R.string.drafts_title,
                ),
                style = KpakamType.cardTitle,
                modifier =
                    Modifier.semantics {
                        heading()
                    },
            )
            state.origin?.let { InferenceBadge(it.source, it.reason) }
            state.transcript?.let { Transcript(it) }
            val lowRoute = state.origin?.confidence == Confidence.LOW
            if (lowRoute) Notice(stringResource(R.string.entry_low_route))
            state.drafts.forEachIndexed { index, item ->
                DraftCard(
                    item,
                    onEdit = { viewModel.startEdit(index) },
                    onRemove = { viewModel.removeDraft(index) },
                )
            }
            DraftActions(state, speech, viewModel)
        }
    }
}

@Composable
private fun Transcript(text: String) {
    val colors = Kpakam.colors
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = colors.text)) {
                append(stringResource(R.string.voice_you_said))
            }
            append(" “$text”")
        },
        style = KpakamType.body,
        color = colors.muted,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.bg)
                .padding(12.dp),
    )
}

@Composable
private fun Notice(text: String) {
    Text(
        text,
        style = KpakamType.small,
        fontWeight = FontWeight.Medium,
        color = Kpakam.colors.accent,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Kpakam.colors.accentSoft)
                .padding(12.dp),
    )
}

@Composable
private fun DraftActions(state: QuickEntryUiState, speech: String, viewModel: QuickEntryViewModel) {
    val count = state.drafts.size
    val saving = state.phase == EntryPhase.Saving
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(Icons.AutoMirrored.Outlined.VolumeUp, stringResource(R.string.voice_read_back), {
            viewModel.readAloud(speech)
        })
        PillButton(
            stringResource(R.string.drafts_discard),
            viewModel::discardDrafts,
            style = PillStyle.Soft,
            large = true,
        )
        PillButton(
            text =
                if (saving) {
                    stringResource(
                        R.string.drafts_saving,
                    )
                } else {
                    pluralStringResource(R.plurals.drafts_save_all, count, count)
                },
            onClick = viewModel::saveAll,
            enabled = !state.busy,
            busy = saving,
            large = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DraftCard(item: DraftUi, onEdit: () -> Unit, onRemove: () -> Unit) {
    val draft = item.draft
    val colors = Kpakam.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(colors.surface2)
                .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KindTile(journalKindOf(draft.kind))
            Column(Modifier.weight(1f)) {
                Text(stringResource(draftKindLabel(draft.kind)), style = KpakamType.rowTitle)
                detailLine(draft).takeIf { it.isNotEmpty() }?.let {
                    Text(
                        it,
                        style = KpakamType.small,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(NairaFormat.formatNaira(draft.totalNaira), style = KpakamType.listAmount)
        }
        if (draft.kind == DraftKind.CREDIT_SALE) OwesLine(draft)
        if (draft.confidence < QuickEntryUiState.LOW_CONFIDENCE) Notice(stringResource(R.string.entry_check_carefully))
        item.error?.let { DraftError(stringResource(ledgerErrorLabel(it))) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
            RoundIconButton(Icons.Outlined.Edit, stringResource(R.string.draft_edit), onEdit, size = 44.dp)
            RoundIconButton(Icons.Outlined.Close, stringResource(R.string.draft_remove), onRemove, size = 44.dp)
        }
    }
}

@Composable
private fun DraftError(text: String) {
    Text(
        text,
        style = KpakamType.small,
        fontWeight = FontWeight.SemiBold,
        color = Kpakam.colors.danger,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
    )
}

@Composable
private fun OwesLine(draft: Draft) {
    val owed = Kobo.fromNaira(draft.totalNaira) - Kobo.fromNaira(draft.paidNaira ?: 0.0)
    if (owed.value <= 0) return
    Text(
        stringResource(R.string.draft_owes_line, draft.customerName ?: "?", NairaFormat.format(owed)),
        style = KpakamType.small,
        fontWeight = FontWeight.SemiBold,
        color = Kpakam.colors.credit,
        modifier =
            Modifier
                .clip(
                    CircleShape,
                ).background(Kpakam.colors.accentSoft)
                .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** "3 bag rice · Mama Bisi": goods, then the customer (credit sales show the customer in the owes line). */
private fun detailLine(draft: Draft): String {
    val goods =
        listOfNotNull(
            draft.quantity?.let(JournalBuilder::formatQuantity),
            draft.unit,
            draft.item,
        ).joinToString(" ")
    val customer = draft.customerName.takeIf { draft.kind != DraftKind.CREDIT_SALE }
    return listOfNotNull(goods.ifBlank { null }, customer).joinToString(" · ")
}

/** One sentence per draft for text-to-speech: "Sale, 3 bag rice, 45,000 naira." */
@Composable
private fun draftSpeech(draft: Draft): String =
    stringResource(
        R.string.voice_speech,
        stringResource(draftKindLabel(draft.kind)),
        JournalBuilder.describe(draft, journalKindOf(draft.kind)),
        NairaFormat.plain(Kobo.fromNaira(draft.totalNaira)),
    )

private fun journalKindOf(kind: DraftKind): JournalKind =
    when (kind) {
        DraftKind.SALE -> JournalKind.SALE
        DraftKind.CREDIT_SALE -> JournalKind.CREDIT_SALE
        DraftKind.REPAYMENT -> JournalKind.REPAYMENT
        DraftKind.EXPENSE -> JournalKind.EXPENSE
    }

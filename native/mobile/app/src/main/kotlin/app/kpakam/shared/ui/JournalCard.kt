package app.kpakam.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.kpakam.R
import app.kpakam.core.ledger.InferenceSource
import app.kpakam.core.ledger.Journal
import app.kpakam.core.ledger.JournalKind
import app.kpakam.core.money.Kobo
import app.kpakam.core.routing.ReasonCode
import app.kpakam.shared.money.NairaFormat
import app.kpakam.shared.time.LagosDates

/** An amount with a sign for money in (+) and out (−), so meaning never relies on colour alone. */
@Composable
fun MoneyText(
    amount: Kobo,
    tone: MoneyTone,
    style: TextStyle,
    modifier: Modifier = Modifier,
    struck: Boolean = false,
) {
    val sign =
        when (tone) {
            MoneyTone.Income -> "+"
            MoneyTone.Expense -> "−"
            MoneyTone.Plain -> ""
        }
    val colors = Kpakam.colors
    Text(
        text = sign + NairaFormat.format(if (tone == MoneyTone.Plain) amount else Kobo(kotlin.math.abs(amount.value))),
        style = style,
        color =
            when {
                struck -> colors.muted
                tone == MoneyTone.Income -> colors.income
                else -> colors.text
            },
        textDecoration = if (struck) TextDecoration.LineThrough else null,
        maxLines = 1,
        modifier = modifier,
    )
}

/** The direction tile for an entry: ↙ money in, ↗ money out, notes for credit. */
@Composable
fun KindTile(kind: JournalKind) {
    val colors = Kpakam.colors
    when (kind) {
        JournalKind.SALE, JournalKind.REPAYMENT -> IconTile(Icons.Outlined.SouthWest)
        JournalKind.EXPENSE -> IconTile(Icons.Outlined.NorthEast)
        JournalKind.CREDIT_SALE ->
            IconTile(
                Icons.Outlined.Payments,
                background = colors.accentSoft,
                tint = colors.credit,
            )
        JournalKind.REVERSAL -> IconTile(Icons.AutoMirrored.Outlined.Undo, tint = colors.muted)
    }
}

/** One entry as its own white card: summary first; tap for where it was read, Undo, and Fix. */
@Composable
fun JournalCard(
    journal: Journal,
    pendingSync: Boolean,
    reversed: Boolean,
    customerName: String?,
    modifier: Modifier = Modifier,
    actions: EntryActions? = null,
) {
    var open by rememberSaveable(journal.id.value) { mutableStateOf(false) }
    val expandedText = stringResource(if (open) R.string.entry_details_shown else R.string.entry_details_hidden)
    Surface(shape = Radii.row, color = Kpakam.colors.surface, modifier = modifier.fillMaxWidth()) {
        Column {
            RowContent(
                title = titleOf(journal, customerName),
                leading = { KindTile(journal.kind) },
                subtitle = { EntrySubtitle(journal, pendingSync) },
                trailing = { EntryAmount(journal, reversed) },
                modifier = Modifier.rowClick { open = !open }.semantics { stateDescription = expandedText },
            )
            AnimatedVisibility(open) {
                EntryDetails(journal, actions.takeIf { journal.kind != JournalKind.REVERSAL && !reversed })
            }
        }
    }
}

@Composable
private fun titleOf(journal: Journal, customerName: String?): String =
    if (journal.kind == JournalKind.REVERSAL) {
        stringResource(R.string.history_reverses)
    } else {
        customerName ?: journal.description.replaceFirstChar { it.uppercase() }
    }

@Composable
private fun RowScope.EntrySubtitle(journal: Journal, pendingSync: Boolean) {
    val colors = Kpakam.colors
    val owed = EntryAmounts.creditOwed(journal)
    val time = LagosDates.time(journal.occurredAt)
    if (owed.value > 0) {
        Row(Modifier.weight(1f, fill = false)) {
            SubtitleText(
                stringResource(R.string.history_still_owed, NairaFormat.format(owed)),
                colors.credit,
                bold = true,
            )
            SubtitleText(" · $time")
        }
    } else {
        SubtitleText(
            "${stringResource(journalKindLabel(journal.kind))} · $time",
            modifier = Modifier.weight(1f, fill = false),
        )
    }
    SourceIcon(journal.source.inference)
    if (pendingSync) {
        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
        SubtitleText(stringResource(R.string.sync_pending), colors.accent, bold = true)
    }
}

@Composable
private fun SourceIcon(source: InferenceSource) {
    val (icon, label) =
        when (source) {
            InferenceSource.ON_DEVICE -> Icons.Outlined.PhoneAndroid to R.string.badge_device
            InferenceSource.IN_CLOUD -> Icons.Outlined.Cloud to R.string.badge_cloud
            InferenceSource.MANUAL -> return
        }
    val description = stringResource(label)
    Icon(icon, contentDescription = description, modifier = Modifier.size(14.dp))
}

@Composable
private fun EntryAmount(journal: Journal, reversed: Boolean) {
    MoneyText(
        EntryAmounts.headline(journal),
        EntryAmounts.tone(journal, reversed),
        KpakamType.rowAmount,
        struck = reversed,
    )
    if (reversed) {
        Text(stringResource(R.string.history_reversed), style = KpakamType.small, color = Kpakam.colors.muted)
    }
}

@Composable
private fun EntryDetails(journal: Journal, actions: EntryActions?) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
    ) {
        if (journal.source.inference != InferenceSource.MANUAL) {
            InferenceBadge(
                journal.source.inference,
                ReasonCode.entries.firstOrNull { it.name == journal.source.reason },
            )
        }
        if (journal.kind != JournalKind.REVERSAL) {
            Text(journal.description, style = KpakamType.small, color = Kpakam.colors.muted)
        }
        actions?.let {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PillButton(
                    stringResource(R.string.history_undo),
                    it.onUndo,
                    style = PillStyle.Secondary,
                    icon = Icons.AutoMirrored.Outlined.Undo,
                )
                PillButton(
                    stringResource(R.string.history_edit),
                    it.onFix,
                    style = PillStyle.Secondary,
                    icon = Icons.Outlined.Edit,
                )
            }
        }
    }
}

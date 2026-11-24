package app.kpakam.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.kpakam.R
import app.kpakam.core.draft.Draft
import app.kpakam.core.draft.DraftKind
import app.kpakam.core.ledger.JournalBuilder
import java.math.BigDecimal

/** The editable text of a draft. */
private data class DraftFields(
    val kind: DraftKind,
    val item: String,
    val total: String,
    val paid: String,
    val customer: String,
) {
    val totalNaira: Double? get() = parseNaira(total)

    /** Applies the edits to [original]. Quantity and unit stay only while the item text is unchanged. */
    fun applyTo(original: Draft): Draft {
        val sameItem = item.trim() == original.item?.trim()
        return original.copy(
            kind = kind,
            item = item.trim().ifEmpty { null },
            totalNaira = totalNaira ?: original.totalNaira,
            paidNaira = if (kind == DraftKind.CREDIT_SALE) parseNaira(paid) ?: 0.0 else null,
            customerName = customer.trim().ifEmpty { null },
            quantity = if (sameItem) original.quantity else null,
            unit = if (sameItem) original.unit else null,
        )
    }

    companion object {
        fun of(draft: Draft) =
            DraftFields(
                kind = draft.kind,
                item = draft.item.orEmpty(),
                total = JournalBuilder.formatQuantity(draft.totalNaira),
                paid = draft.paidNaira?.let(JournalBuilder::formatQuantity).orEmpty(),
                customer = draft.customerName.orEmpty(),
            )
    }
}

/** Lets the trader change a draft before saving it, or fix a saved entry (saved as reversal plus new entry). */
@Composable
fun DraftEditorDialog(draft: Draft, onDone: (Draft) -> Unit, onDismiss: () -> Unit) {
    var fields by remember { mutableStateOf(DraftFields.of(draft)) }
    val total = fields.totalNaira
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_title)) },
        text = { EditorFields(fields) { fields = it } },
        confirmButton = {
            TextButton(enabled = total != null && total > 0, onClick = { onDone(fields.applyTo(draft)) }) {
                Text(stringResource(R.string.edit_done))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.edit_cancel)) } },
    )
}

@Composable
private fun EditorFields(fields: DraftFields, onChange: (DraftFields) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KindPicker(fields.kind) { onChange(fields.copy(kind = it)) }
        OutlinedTextField(
            value = fields.item,
            onValueChange = { onChange(fields.copy(item = it.take(MAX_ITEM))) },
            label = { Text(stringResource(R.string.edit_item)) },
        )
        MoneyField(fields.total, R.string.edit_total) { onChange(fields.copy(total = it)) }
        if (fields.kind == DraftKind.CREDIT_SALE) {
            MoneyField(fields.paid, R.string.edit_paid) { onChange(fields.copy(paid = it)) }
        }
        OutlinedTextField(
            value = fields.customer,
            onValueChange = { onChange(fields.copy(customer = it.take(MAX_NAME))) },
            label = { Text(stringResource(R.string.edit_customer)) },
        )
    }
}

@Composable
private fun KindPicker(selected: DraftKind, onSelect: (DraftKind) -> Unit) {
    Text(stringResource(R.string.edit_kind))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DraftKind.entries.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(stringResource(draftKindLabel(option))) },
            )
        }
    }
}

@Composable
private fun MoneyField(value: String, label: Int, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }) },
        label = { Text(stringResource(label)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
    )
}

private const val MAX_ITEM = 80
private const val MAX_NAME = 60

private fun parseNaira(text: String): Double? =
    text
        .replace(",", "")
        .trim()
        .toBigDecimalOrNull()
        ?.takeIf { it > BigDecimal.ZERO }
        ?.toDouble()

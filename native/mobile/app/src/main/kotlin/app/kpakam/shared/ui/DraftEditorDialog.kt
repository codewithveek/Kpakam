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

/** Lets the trader change a draft before saving it, or fix a saved entry (saved as reversal plus new entry). */
@Composable
fun DraftEditorDialog(draft: Draft, onDone: (Draft) -> Unit, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf(draft.kind) }
    var item by remember { mutableStateOf(draft.item.orEmpty()) }
    var total by remember { mutableStateOf(nairaText(draft.totalNaira)) }
    var paid by remember { mutableStateOf(draft.paidNaira?.let(::nairaText).orEmpty()) }
    var customer by remember { mutableStateOf(draft.customerName.orEmpty()) }
    val totalValue = parseNaira(total)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KindPicker(kind) { kind = it }
                OutlinedTextField(
                    item,
                    { item = it.take(MAX_ITEM) },
                    label = { Text(stringResource(R.string.edit_item)) },
                )
                MoneyField(total, R.string.edit_total) { total = it }
                if (kind == DraftKind.CREDIT_SALE) MoneyField(paid, R.string.edit_paid) { paid = it }
                OutlinedTextField(
                    customer,
                    { customer = it.take(MAX_NAME) },
                    label = { Text(stringResource(R.string.edit_customer)) },
                )
            }
        },
        confirmButton = {
            TextButton(enabled = totalValue != null && totalValue > 0, onClick = {
                onDone(
                    draft.copy(
                        kind = kind,
                        item = item.trim().ifEmpty { null },
                        totalNaira = totalValue ?: draft.totalNaira,
                        paidNaira = if (kind == DraftKind.CREDIT_SALE) parseNaira(paid) ?: 0.0 else null,
                        customerName = customer.trim().ifEmpty { null },
                        quantity = if (item.trim() == draft.item?.trim()) draft.quantity else null,
                        unit = if (item.trim() == draft.item?.trim()) draft.unit else null,
                    ),
                )
            }) { Text(stringResource(R.string.edit_done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.edit_cancel)) } },
    )
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

private fun nairaText(naira: Double): String = JournalBuilder.formatQuantity(naira)

private fun parseNaira(text: String): Double? =
    text
        .replace(",", "")
        .trim()
        .takeIf {
            it.isNotEmpty()
        }?.toBigDecimalOrNull()
        ?.takeIf { it > BigDecimal.ZERO }
        ?.toDouble()

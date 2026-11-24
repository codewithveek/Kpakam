package app.kpakam.features.customers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kpakam.R
import app.kpakam.features.ledger.CorrectionDialogs
import app.kpakam.features.ledger.CorrectionsViewModel
import app.kpakam.features.ledger.entryCards
import app.kpakam.shared.money.NairaFormat
import app.kpakam.shared.ui.InitialsTile
import app.kpakam.shared.ui.KCard
import app.kpakam.shared.ui.Kpakam
import app.kpakam.shared.ui.KpakamType
import app.kpakam.shared.ui.PageGutter
import app.kpakam.shared.ui.PillButton
import app.kpakam.shared.ui.PillStyle
import app.kpakam.shared.ui.Radii
import app.kpakam.shared.ui.SectionHeader

/** Height kept free at the bottom of the list for the floating payment bar. */
val PaymentBarSpace = 112.dp

/** One customer: balance (derived from journal lines), history, and a floating "Record payment" bar. */
@Composable
fun CustomerDetailScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: CustomerDetailViewModel = hiltViewModel(),
    corrections: CorrectionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val amount by viewModel.paymentText.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            customerItems(state, corrections, onBack)
        }
        if (state.customer != null) {
            PaymentBar(
                amount = amount,
                onChange = viewModel::onPaymentChange,
                onSubmit = viewModel::recordPayment,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
    CorrectionDialogs(corrections)
}

@Composable
private fun BackPill(onBack: () -> Unit) {
    Box(Modifier.padding(horizontal = PageGutter)) {
        PillButton(
            stringResource(R.string.back),
            onBack,
            style = PillStyle.Secondary,
            icon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
        )
    }
}

@Composable
private fun ProfileCard(name: String, balance: app.kpakam.core.money.Kobo) {
    val owes = balance.value > 0
    KCard(
        modifier = Modifier.padding(horizontal = PageGutter, vertical = 8.dp),
        shape = Radii.hero,
        padding = PaddingValues(24.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            InitialsTile(name, large = true)
            Text(name, style = KpakamType.name)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(NairaFormat.format(balance), style = KpakamType.bigAmount)
                Text(
                    balanceLabel(balance),
                    style = KpakamType.body,
                    color = if (owes) Kpakam.colors.credit else Kpakam.colors.muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private fun LazyListScope.customerItems(
    state: CustomerDetailUiState,
    corrections: CorrectionsViewModel,
    onBack: () -> Unit,
) {
    item { BackPill(onBack) }
    state.customer?.let { customer -> item { ProfileCard(customer.name, state.balance) } }
    if (!state.loading && state.customer == null) {
        item { Text(stringResource(R.string.customer_not_found), Modifier.padding(horizontal = PageGutter)) }
    }
    if (state.entries.isNotEmpty()) {
        item {
            SectionHeader(
                stringResource(R.string.customer_history),
                Modifier.padding(horizontal = PageGutter),
            )
        }
    }
    entryCards(state.entries, state.customer?.let { mapOf(it.id to it.name) }.orEmpty(), state.reversed, corrections)
}

/** The main bar is hidden on this page, so recording a payment floats at the bottom, in thumb reach. */
@Composable
private fun PaymentBar(amount: String, onChange: (String) -> Unit, onSubmit: () -> Unit, modifier: Modifier) {
    val colors = Kpakam.colors
    Surface(
        shape = CircleShape,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        modifier =
            modifier
                .navigationBarsPadding()
                .padding(horizontal = PageGutter, vertical = 16.dp)
                .shadow(16.dp, CircleShape)
                .fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            Text(
                "₦",
                style = KpakamType.payment,
                color = colors.muted,
                modifier = Modifier.padding(start = 16.dp, end = 8.dp),
            )
            AmountField(amount, onChange, onSubmit, Modifier.weight(1f))
            PillButton(
                stringResource(R.string.customer_record_payment),
                onSubmit,
                enabled = amount.isNotBlank(),
                large = true,
            )
        }
    }
}

@Composable
private fun AmountField(amount: String, onChange: (String) -> Unit, onSubmit: () -> Unit, modifier: Modifier) {
    val colors = Kpakam.colors
    val label = stringResource(R.string.customer_amount_paid)
    Box(modifier.heightIn(min = 56.dp), contentAlignment = Alignment.CenterStart) {
        if (amount.isEmpty()) Text("0", style = KpakamType.payment, color = colors.muted)
        BasicTextField(
            value = amount,
            onValueChange = onChange,
            singleLine = true,
            textStyle = KpakamType.payment.copy(color = colors.text),
            cursorBrush = SolidColor(colors.text),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
    }
}

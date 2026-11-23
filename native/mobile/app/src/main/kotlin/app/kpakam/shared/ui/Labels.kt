package app.kpakam.shared.ui

import androidx.annotation.StringRes
import app.kpakam.R
import app.kpakam.core.draft.DraftKind
import app.kpakam.core.ledger.JournalKind
import app.kpakam.core.ledger.LedgerError
import app.kpakam.core.routing.ModelState

/** Plain-English label for a draft kind. */
@StringRes
fun draftKindLabel(kind: DraftKind): Int =
    when (kind) {
        DraftKind.SALE -> R.string.kind_sale
        DraftKind.CREDIT_SALE -> R.string.kind_credit_sale
        DraftKind.REPAYMENT -> R.string.kind_repayment
        DraftKind.EXPENSE -> R.string.kind_expense
    }

/** Plain-English label for a journal kind. */
@StringRes
fun journalKindLabel(kind: JournalKind): Int =
    when (kind) {
        JournalKind.SALE -> R.string.kind_sale
        JournalKind.CREDIT_SALE -> R.string.kind_credit_sale
        JournalKind.REPAYMENT -> R.string.kind_repayment
        JournalKind.EXPENSE -> R.string.kind_expense
        JournalKind.REVERSAL -> R.string.kind_reversal
    }

/** What the trader must fix for a ledger error. */
@StringRes
fun ledgerErrorLabel(error: LedgerError): Int =
    when (error) {
        LedgerError.CustomerRequired -> R.string.draft_error_customer
        LedgerError.PaidExceedsTotal -> R.string.draft_error_paid
        LedgerError.AmountTooSmall -> R.string.draft_error_amount
        LedgerError.AlreadyReversed -> R.string.history_already_undone
        is LedgerError.InvariantViolated -> R.string.draft_error_other
    }

/** Plain-English label for the on-device model state. */
@StringRes
fun modelStateLabel(state: ModelState): Int =
    when (state) {
        ModelState.AVAILABLE -> R.string.model_available
        ModelState.DOWNLOADABLE -> R.string.model_downloadable
        ModelState.DOWNLOADING -> R.string.model_downloading
        ModelState.UNAVAILABLE -> R.string.model_unavailable
    }

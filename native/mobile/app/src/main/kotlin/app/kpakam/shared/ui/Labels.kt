package app.kpakam.shared.ui

import androidx.annotation.StringRes
import app.kpakam.R
import app.kpakam.core.draft.DraftKind
import app.kpakam.core.ledger.JournalKind
import app.kpakam.core.ledger.LedgerError

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
        JournalKind.SALE -> R.string.ledger_kind_sale
        JournalKind.CREDIT_SALE -> R.string.ledger_kind_credit_sale
        JournalKind.REPAYMENT -> R.string.ledger_kind_repayment
        JournalKind.EXPENSE -> R.string.ledger_kind_expense
        JournalKind.REVERSAL -> R.string.ledger_kind_reversal
    }

/** What the trader must fix for a ledger error. */
@StringRes
fun ledgerErrorLabel(error: LedgerError): Int =
    when (error) {
        LedgerError.CustomerRequired -> R.string.error_customer_required
        LedgerError.PaidExceedsTotal -> R.string.error_paid_exceeds_total
        LedgerError.AmountTooSmall -> R.string.error_amount_too_small
        LedgerError.AlreadyReversed -> R.string.error_already_reversed
        is LedgerError.InvariantViolated -> R.string.error_generic
    }

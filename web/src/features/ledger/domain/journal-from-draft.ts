import type { Draft } from '@/inference/domain/draft';
import { customerOfAccount, type Journal } from './journal';

function amountOf(journal: Journal, account: 'cash' | 'sales' | 'expenses'): number {
  return journal.lines.filter((l) => l.account === account).reduce((sum, l) => sum + l.amountKobo, 0) / 100;
}

function receivableTotal(journal: Journal): number {
  return journal.lines.filter((l) => customerOfAccount(l.account) !== null).reduce((sum, l) => sum + l.amountKobo, 0) / 100;
}

/**
 * Rebuilds an editable draft from a stored journal, for "Fix" (reverse + new entry).
 * Quantity and unit are folded into `item`, because journals keep only the description.
 * @returns A draft, or null for reversal journals (they cannot be edited).
 */
export function journalToDraft(journal: Journal, customerName: string | null): Draft | null {
  const base = { item: journal.description, quantity: null, unit: null, customerName, confidence: 1 };
  switch (journal.kind) {
    case 'sale':
      return { ...base, kind: 'sale', totalNaira: -amountOf(journal, 'sales'), paidNaira: null };
    case 'expense':
      return { ...base, kind: 'expense', totalNaira: amountOf(journal, 'expenses'), paidNaira: null };
    case 'repayment':
      return { ...base, kind: 'repayment', totalNaira: -receivableTotal(journal), paidNaira: null };
    case 'credit_sale':
      return { ...base, kind: 'credit_sale', totalNaira: -amountOf(journal, 'sales'), paidNaira: amountOf(journal, 'cash') };
    case 'reversal':
      return null;
  }
}

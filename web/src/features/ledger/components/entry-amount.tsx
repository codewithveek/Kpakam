import { asKobo } from '@/shared/money/kobo';
import { formatNaira } from '@/shared/money/format';
import { MESSAGES } from '@/shared/messages/en';
import { Money, type MoneyTone } from '@/shared/ui/money';
import { customerOfAccount, type Journal } from '../domain/journal';

/** The headline amount: what the trader thinks of as "the amount" for each kind. */
export function headlineAmount(journal: Journal): number {
  const sum = (match: (account: string) => boolean) => journal.lines.filter((l) => match(l.account)).reduce((s, l) => s + l.amountKobo, 0);
  switch (journal.kind) {
    case 'sale':
    case 'credit_sale':
      return -sum((a) => a === 'sales');
    case 'expense':
      return sum((a) => a === 'expenses');
    case 'repayment':
      return sum((a) => a === 'cash');
    case 'reversal':
      return journal.lines.reduce((s, l) => s + Math.abs(l.amountKobo), 0) / 2;
  }
}

const TONE: Readonly<Record<Journal['kind'], MoneyTone>> = {
  sale: 'income',
  repayment: 'income',
  credit_sale: 'income',
  expense: 'expense',
  reversal: 'plain',
};

/** The part of a credit sale still owed, in kobo (0 for other kinds). */
export function creditOwed(journal: Journal): number {
  if (journal.kind !== 'credit_sale') return 0;
  return journal.lines.filter((l) => customerOfAccount(l.account) !== null).reduce((s, l) => s + l.amountKobo, 0);
}

/** "₦10,000 on credit", shown in the row subtitle. */
export function creditLabel(journal: Journal): string | null {
  const owed = creditOwed(journal);
  return owed > 0 ? MESSAGES.ledger.onCredit(formatNaira(asKobo(owed))) : null;
}

/** Right-hand side of a row: the signed amount, and "Undone" for reversed entries. */
export function EntryAmount({ journal, reversed }: { readonly journal: Journal; readonly reversed: boolean }) {
  return (
    <>
      <Money
        amount={asKobo(headlineAmount(journal))}
        tone={reversed ? 'plain' : TONE[journal.kind]}
        className={`text-[16px] font-semibold ${reversed ? 'text-muted line-through' : ''}`}
      />
      {reversed && <span className="text-[13px] font-medium text-muted">{MESSAGES.ledger.reversed}</span>}
    </>
  );
}

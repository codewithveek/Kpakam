import { Avatar } from './avatar';
import { ArrowDownLeftIcon, ArrowUpRightIcon, HandCoinsIcon, UndoIcon } from './icons';

export type EntryKind = 'sale' | 'credit_sale' | 'repayment' | 'expense' | 'reversal';

const ICONS = { sale: ArrowDownLeftIcon, repayment: ArrowDownLeftIcon, credit_sale: HandCoinsIcon, expense: ArrowUpRightIcon, reversal: UndoIcon } as const;
const TONES: Readonly<Record<EntryKind, string>> = {
  sale: 'bg-device-soft text-income',
  repayment: 'bg-cloud-soft text-cloud',
  credit_sale: 'bg-accent-soft text-credit',
  expense: 'bg-surface-2 text-text',
  reversal: 'bg-surface-2 text-muted',
};

/** A direction icon per entry kind: ↙ money in, ↗ money out, coins for credit. Decorative. */
export function KindAvatar({ kind }: { readonly kind: EntryKind }) {
  const Icon = ICONS[kind];
  return <Avatar icon={<Icon width={20} height={20} />} tone={TONES[kind]} />;
}

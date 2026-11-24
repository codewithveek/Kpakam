import { formatNaira } from '@/shared/money/format';
import type { Kobo } from '@/shared/money/kobo';

export type MoneyTone = 'income' | 'expense' | 'credit' | 'plain';

const TONE: Readonly<Record<MoneyTone, string>> = {
  income: 'text-income',
  // Money out stays neutral: red is reserved for errors. The − sign carries the meaning.
  expense: '',
  credit: 'text-credit',
  plain: '',
};

const SIGN: Readonly<Record<MoneyTone, string>> = { income: '+', expense: '−', credit: '', plain: '' };

export interface MoneyProps {
  readonly amount: Kobo;
  readonly tone?: MoneyTone;
  readonly className?: string;
  readonly testId?: string;
}

/**
 * An amount in naira with tabular figures. Money in and out also carry a sign (+ / −),
 * so the meaning never relies on colour alone.
 */
export function Money({ amount, tone = 'plain', className = '', testId }: MoneyProps) {
  const text = formatNaira(Math.abs(amount) as Kobo);
  return (
    <span className={`amount ${TONE[tone]} ${className}`} data-testid={testId}>
      {SIGN[tone]}
      {amount < 0 && tone === 'plain' ? '−' : ''}
      {text}
    </span>
  );
}

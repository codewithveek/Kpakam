import { useId } from 'react';
import { DRAFT_KINDS, type Draft } from '@/inference/domain/draft';
import { MESSAGES } from '@/shared/messages/en';

export interface DraftEditorProps {
  readonly draft: Draft;
  readonly onChange: (draft: Draft) => void;
}

function numberOrNull(value: string): number | null {
  const n = Number(value.replace(/[,₦\s]/g, ''));
  return value.trim() === '' || !Number.isFinite(n) ? null : n;
}

function textOrNull(value: string): string | null {
  return value.trim() === '' ? null : value;
}

const FIELD = 'flex flex-col gap-1 text-[13px] font-semibold text-muted';
const INPUT = 'min-h-12 rounded-[14px] bg-surface px-4 text-base font-medium text-text';

/** Edit one draft before saving. Labels on every field; works with keyboard only. */
export function DraftEditor({ draft, onChange }: DraftEditorProps) {
  const id = useId();
  const set = (patch: Partial<Draft>) => onChange({ ...draft, ...patch });
  const needsCustomer = draft.kind !== 'expense';
  return (
    <fieldset className="grid grid-cols-2 gap-3">
      <legend className="sr-only">{MESSAGES.drafts.edit}</legend>
      <label className={`${FIELD} col-span-2`} htmlFor={`${id}-kind`}>
        {MESSAGES.drafts.kind.sale} / {MESSAGES.drafts.kind.expense}
        <select
          id={`${id}-kind`}
          className={INPUT}
          value={draft.kind}
          onChange={(e) => set({ kind: e.target.value as Draft['kind'], paidNaira: e.target.value === 'credit_sale' ? (draft.paidNaira ?? 0) : null })}
        >
          {DRAFT_KINDS.map((kind) => (
            <option key={kind} value={kind}>
              {MESSAGES.drafts.kind[kind]}
            </option>
          ))}
        </select>
      </label>
      <label className={FIELD} htmlFor={`${id}-total`}>
        {MESSAGES.drafts.total} (₦)
        <input
          id={`${id}-total`}
          className={INPUT}
          inputMode="decimal"
          value={draft.totalNaira}
          onChange={(e) => set({ totalNaira: numberOrNull(e.target.value) ?? 0 })}
        />
      </label>
      {draft.kind === 'credit_sale' && (
        <label className={FIELD} htmlFor={`${id}-paid`}>
          {MESSAGES.drafts.paid} (₦)
          <input
            id={`${id}-paid`}
            className={INPUT}
            inputMode="decimal"
            value={draft.paidNaira ?? 0}
            onChange={(e) => set({ paidNaira: numberOrNull(e.target.value) ?? 0 })}
          />
        </label>
      )}
      {needsCustomer && (
        <label className={`${FIELD} col-span-2`} htmlFor={`${id}-customer`}>
          {MESSAGES.drafts.customer}
          <input
            id={`${id}-customer`}
            className={INPUT}
            value={draft.customerName ?? ''}
            maxLength={60}
            onChange={(e) => set({ customerName: textOrNull(e.target.value) })}
          />
        </label>
      )}
      <label className={`${FIELD} col-span-2`} htmlFor={`${id}-item`}>
        {MESSAGES.drafts.item}
        <input id={`${id}-item`} className={INPUT} value={draft.item ?? ''} maxLength={80} onChange={(e) => set({ item: textOrNull(e.target.value) })} />
      </label>
      <label className={FIELD} htmlFor={`${id}-qty`}>
        {MESSAGES.drafts.quantity}
        <input
          id={`${id}-qty`}
          className={INPUT}
          inputMode="decimal"
          value={draft.quantity ?? ''}
          onChange={(e) => set({ quantity: numberOrNull(e.target.value) })}
        />
      </label>
      <label className={FIELD} htmlFor={`${id}-unit`}>
        {MESSAGES.drafts.unit}
        <input id={`${id}-unit`} className={INPUT} value={draft.unit ?? ''} maxLength={30} onChange={(e) => set({ unit: textOrNull(e.target.value) })} />
      </label>
    </fieldset>
  );
}

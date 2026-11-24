import { describe, expect, it } from 'vitest';
import type { Draft } from '@/inference/domain/draft';
import { newUlid } from '@/shared/ids/ulid';
import { createInMemoryLedger } from '@/test/in-memory-ledger';
import { computeBalances } from '../domain/totals';
import { createLedgerService, type ConfirmedDraft } from './ledger-service';

const SOURCE = { inference: 'on_device', reason: 'DEFAULT_DEVICE', promptVersion: 'parse-v1' } as const;
const credit: Draft = {
  kind: 'credit_sale',
  item: 'rice',
  quantity: 3,
  unit: 'bag',
  totalNaira: 45000,
  paidNaira: 35000,
  customerName: 'Mama Bisi',
  confidence: 0.9,
};
const repay: Draft = {
  kind: 'repayment',
  item: null,
  quantity: null,
  unit: null,
  totalNaira: 4000,
  paidNaira: null,
  customerName: 'mama  bisi',
  confidence: 0.9,
};

function confirmed(draft: Draft, key = newUlid() as string): ConfirmedDraft {
  return { draft, journalId: newUlid(), idempotencyKey: key, occurredAt: new Date('2026-11-24T09:00:00Z'), source: SOURCE };
}

function setup() {
  const ledger = createInMemoryLedger();
  const service = createLedgerService({ journals: ledger.journalRepo, customers: ledger.customerRepo });
  return { ledger, service };
}

describe('ledger service', () => {
  it('creates the customer and journal in one batch, with the cached balance', async () => {
    const { ledger, service } = setup();
    const result = await service.confirmDrafts([confirmed(credit)]);
    expect(result.ok && result.value).toHaveLength(1);
    expect(ledger.commits).toHaveLength(1);
    const [customer] = ledger.customers.values();
    expect(customer).toMatchObject({ name: 'Mama Bisi', nameKey: 'mama bisi', balanceKobo: 1_000_000 });
  });

  it('matches an existing customer by name and increments the balance', async () => {
    const { ledger, service } = setup();
    await service.confirmDrafts([confirmed(credit)]);
    await service.confirmDrafts([confirmed(repay)]);
    expect(ledger.customers.size).toBe(1);
    expect([...ledger.customers.values()][0]?.balanceKobo).toBe(600_000);
    expect(ledger.commits[1]?.customers).toEqual([expect.objectContaining({ type: 'increment', delta: -400_000 })]);
  });

  it('creates one customer when two drafts in a batch name the same new customer', async () => {
    const { ledger, service } = setup();
    await service.confirmDrafts([confirmed(credit), confirmed(repay)]);
    expect(ledger.customers.size).toBe(1);
    expect([...ledger.customers.values()][0]?.balanceKobo).toBe(600_000);
  });

  it('treats a repeated journal id as a no-op', async () => {
    const { ledger, service } = setup();
    const item = confirmed(credit);
    await service.confirmDrafts([item]);
    const again = await service.confirmDrafts([item]);
    expect(again.ok && again.value).toEqual([]);
    expect(ledger.journals.size).toBe(1);
    expect(ledger.commits).toHaveLength(1);
  });

  it('writes nothing when any draft breaks a ledger rule', async () => {
    const { ledger, service } = setup();
    const bad = confirmed({ ...repay, customerName: null });
    expect(await service.confirmDrafts([confirmed(credit), bad])).toEqual({ ok: false, error: 'CUSTOMER_REQUIRED' });
    expect(ledger.journals.size).toBe(0);
  });

  it('undoes an entry once, restoring the balance, and keeps both records', async () => {
    const { ledger, service } = setup();
    const saved = await service.confirmDrafts([confirmed(credit)]);
    if (!saved.ok || saved.value[0] === undefined) throw new Error('fixture');
    const undo = await service.reverseEntry(saved.value[0]);
    expect(undo.ok && undo.value.reverses).toBe(saved.value[0].id);
    expect([...ledger.customers.values()][0]?.balanceKobo).toBe(0);
    expect(ledger.journals.size).toBe(2);
    expect(await service.reverseEntry(saved.value[0])).toEqual({ ok: false, error: 'ALREADY_REVERSED' });
  });

  it('fixes an entry with a reversal plus a corrected journal in one batch', async () => {
    const { ledger, service } = setup();
    const saved = await service.confirmDrafts([confirmed(credit)]);
    if (!saved.ok || saved.value[0] === undefined) throw new Error('fixture');
    const fixed = await service.fixEntry(saved.value[0], { ...credit, paidNaira: 40000 });
    expect(fixed.ok).toBe(true);
    expect(ledger.commits).toHaveLength(2);
    expect(ledger.commits[1]?.journals.map((j) => j.kind)).toEqual(['reversal', 'credit_sale']);
    const balances = computeBalances([...ledger.journals.values()]);
    const [customer] = ledger.customers.values();
    expect(customer?.balanceKobo).toBe(500_000);
    expect(balances.get(customer?.id ?? '')).toBe(500_000);
  });
});

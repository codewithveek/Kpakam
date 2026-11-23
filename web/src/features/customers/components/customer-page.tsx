import { Link } from '@tanstack/react-router';
import { useId, useState, type SyntheticEvent } from 'react';
import { useAppStore } from '@/app/app-store';
import { useServices } from '@/app/services';
import { JournalList, useCustomerJournals } from '@/features/ledger';
import { newUlid, parseCustomerId, type CustomerId } from '@/shared/ids/ulid';
import { formatNaira } from '@/shared/money/format';
import { MESSAGES } from '@/shared/messages/en';
import { Button } from '@/shared/ui/button';
import { Card } from '@/shared/ui/card';
import { ChevronLeftIcon } from '@/shared/ui/icons';
import type { Customer } from '@/features/ledger';
import { useCustomers } from '@/features/ledger';

function RecordPayment({ customer }: { readonly customer: Customer }) {
  const { ledger } = useServices();
  const showToast = useAppStore((s) => s.showToast);
  const [amount, setAmount] = useState('');
  const id = useId();
  async function submit(event: SyntheticEvent) {
    event.preventDefault();
    const naira = Number(amount.replace(/[,₦\s]/g, ''));
    if (!(naira > 0)) return;
    const draft = {
      kind: 'repayment',
      item: null,
      quantity: null,
      unit: null,
      totalNaira: naira,
      paidNaira: null,
      customerName: customer.name,
      confidence: 1,
    } as const;
    const result = await ledger.confirmDrafts([
      {
        draft,
        journalId: newUlid(),
        idempotencyKey: `manual:${newUlid()}`,
        occurredAt: new Date(),
        source: { inference: 'manual', reason: 'MANUAL', promptVersion: null },
      },
    ]);
    showToast(result.ok ? MESSAGES.drafts.saved(1) : MESSAGES.errors.saveFailed);
    if (result.ok) setAmount('');
  }
  return (
    <form onSubmit={(e) => void submit(e)} className="flex items-end gap-2">
      <label htmlFor={id} className="flex flex-1 flex-col gap-1 text-sm font-semibold">
        {MESSAGES.customers.amountPaid}
        <input
          id={id}
          inputMode="decimal"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          className="min-h-12 rounded-lg border border-border bg-bg px-3 text-base font-normal"
        />
      </label>
      <Button type="submit" disabled={amount.trim() === ''}>
        {MESSAGES.customers.recordPayment}
      </Button>
    </form>
  );
}

function CustomerDetail({ customer, customerId }: { readonly customer: Customer; readonly customerId: CustomerId }) {
  const view = useCustomerJournals(customerId);
  const { byId } = useCustomers();
  return (
    <>
      <Card className="flex flex-col gap-3">
        <h1 className="text-xl font-bold">{customer.name}</h1>
        <p className="amount text-3xl font-bold" data-testid="customer-balance">
          {formatNaira(customer.balanceKobo)}
        </p>
        <p className="text-sm text-muted">{customer.balanceKobo > 0 ? MESSAGES.customers.owes : MESSAGES.customers.settled}</p>
        <RecordPayment customer={customer} />
      </Card>
      <h2 className="font-bold">{MESSAGES.customers.history}</h2>
      <JournalList entries={view.entries} reversed={view.reversed} customers={byId} label={MESSAGES.customers.history} />
    </>
  );
}

/** One customer: balance (derived from journal lines), history, and record payment. */
export function CustomerPage({ customerId }: { readonly customerId: string }) {
  const { byId, loading } = useCustomers();
  const id = parseCustomerId(customerId);
  const customer = id === null ? undefined : byId.get(id);
  return (
    <div className="flex flex-col gap-4">
      <Link to="/customers" className="inline-flex min-h-12 items-center gap-1 font-semibold text-primary">
        <ChevronLeftIcon />
        {MESSAGES.customers.back}
      </Link>
      {customer !== undefined && id !== null ? <CustomerDetail customer={customer} customerId={id} /> : !loading && <p>{MESSAGES.customers.notFound}</p>}
    </div>
  );
}

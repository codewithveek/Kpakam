import { Link } from '@tanstack/react-router';
import { useId, useState, type SyntheticEvent } from 'react';
import { useAppStore } from '@/app/app-store';
import { useServices } from '@/app/services';
import { JournalList, useCustomerJournals, useCustomers, type Customer } from '@/features/ledger';
import { newUlid, parseCustomerId, type CustomerId } from '@/shared/ids/ulid';
import { formatNaira } from '@/shared/money/format';
import { MESSAGES } from '@/shared/messages/en';
import { Avatar } from '@/shared/ui/avatar';
import { Button } from '@/shared/ui/button';
import { ChevronLeftIcon } from '@/shared/ui/icons';
import { SectionHeader } from '@/shared/ui/list';

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
    const source = { inference: 'manual', reason: 'MANUAL', promptVersion: null } as const;
    const result = await ledger.confirmDrafts([{ draft, journalId: newUlid(), idempotencyKey: `manual:${newUlid()}`, occurredAt: new Date(), source }]);
    showToast(result.ok ? MESSAGES.drafts.saved(1) : MESSAGES.errors.saveFailed);
    if (result.ok) setAmount('');
  }
  return (
    // The main bar is hidden on this page, so recording a payment floats at the bottom, in thumb reach.
    <form onSubmit={(e) => void submit(e)} className="fixed inset-x-0 bottom-0 z-40 mx-auto max-w-2xl px-4 pb-[calc(1rem+env(safe-area-inset-bottom))]">
      <div className="flex items-center gap-2 rounded-full border border-border bg-surface p-2 shadow-float">
        <label htmlFor={id} className="sr-only">
          {MESSAGES.customers.amountPaid}
        </label>
        <span aria-hidden="true" className="pl-4 text-[20px] font-semibold text-muted">
          ₦
        </span>
        <input
          id={id}
          inputMode="decimal"
          placeholder="0"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          className="amount min-h-14 min-w-0 flex-1 bg-transparent text-[20px] font-semibold outline-none"
        />
        <Button type="submit" size="lg" disabled={amount.trim() === ''}>
          {MESSAGES.customers.recordPayment}
        </Button>
      </div>
    </form>
  );
}

function CustomerDetail({ customer, customerId }: { readonly customer: Customer; readonly customerId: CustomerId }) {
  const view = useCustomerJournals(customerId);
  const { byId } = useCustomers();
  const owes = customer.balanceKobo > 0;
  return (
    <>
      <section className="flex flex-col items-center gap-4 rounded-[32px] bg-surface p-6 text-center">
        <Avatar name={customer.name} size="lg" />
        <h1 className="display text-[26px] font-bold">{customer.name}</h1>
        <div>
          <p className="amount display text-[44px] font-bold" data-testid="customer-balance">
            {formatNaira(customer.balanceKobo)}
          </p>
          <p className={`mt-1 text-[15px] font-medium ${owes ? 'text-credit' : 'text-muted'}`}>{owes ? MESSAGES.customers.owes : MESSAGES.customers.settled}</p>
        </div>
      </section>
      <RecordPayment customer={customer} />
      <SectionHeader title={MESSAGES.customers.history} />
      <JournalList entries={view.entries} reversed={view.reversed} customers={byId} label={MESSAGES.customers.history} />
    </>
  );
}

/** One customer: balance (derived from journal lines), record a payment, history. */
export function CustomerPage({ customerId }: { readonly customerId: string }) {
  const { byId, loading } = useCustomers();
  const id = parseCustomerId(customerId);
  const customer = id === null ? undefined : byId.get(id);
  return (
    <div className="flex flex-col gap-4">
      <Link
        to="/customers"
        className="press inline-flex min-h-12 items-center gap-1 self-start rounded-full bg-surface pl-3 pr-5 text-[15px] font-semibold text-text"
      >
        <ChevronLeftIcon />
        {MESSAGES.customers.back}
      </Link>
      {customer !== undefined && id !== null ? <CustomerDetail customer={customer} customerId={id} /> : !loading && <p>{MESSAGES.customers.notFound}</p>}
    </div>
  );
}

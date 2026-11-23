import { Link } from '@tanstack/react-router';
import { formatNaira } from '@/shared/money/format';
import { sumKobo } from '@/shared/money/kobo';
import { MESSAGES } from '@/shared/messages/en';
import { Card } from '@/shared/ui/card';
import { ChevronRightIcon } from '@/shared/ui/icons';
import type { Customer } from '@/features/ledger';
import { useCustomers } from '@/features/ledger';

function BalanceText({ customer }: { readonly customer: Customer }) {
  if (customer.balanceKobo > 0) return <span className="amount font-bold">{formatNaira(customer.balanceKobo)}</span>;
  if (customer.balanceKobo < 0)
    return (
      <span className="text-sm text-muted">
        {MESSAGES.customers.inCredit} {formatNaira(customer.balanceKobo)}
      </span>
    );
  return <span className="text-sm text-muted">{MESSAGES.customers.settled}</span>;
}

/** F3: who owes the trader, largest debts first. */
export function DebtBookPage() {
  const { customers, loading } = useCustomers();
  const owed = sumKobo(customers.filter((c) => c.balanceKobo > 0).map((c) => c.balanceKobo));
  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-xl font-bold">{MESSAGES.customers.title}</h1>
      <Card>
        <p className="text-sm text-muted">{MESSAGES.customers.totalOwed}</p>
        <p className="amount text-3xl font-bold" data-testid="total-owed">
          {formatNaira(owed)}
        </p>
      </Card>
      {!loading && customers.length === 0 && <p className="text-muted">{MESSAGES.customers.empty}</p>}
      <ul className="flex flex-col gap-2" aria-label={MESSAGES.customers.title}>
        {customers.map((customer) => (
          <li key={customer.id}>
            <Link
              to="/customers/$customerId"
              params={{ customerId: customer.id }}
              className="flex min-h-14 items-center justify-between gap-3 rounded-xl border border-border bg-surface px-4 py-3 hover:border-primary"
              data-testid="customer-row"
            >
              <span className="font-semibold">{customer.name}</span>
              <span className="flex items-center gap-2">
                <BalanceText customer={customer} />
                <ChevronRightIcon width={18} height={18} />
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}

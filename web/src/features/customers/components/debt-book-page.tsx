import { Link } from '@tanstack/react-router';
import { useCustomers, type Customer } from '@/features/ledger';
import { formatNaira } from '@/shared/money/format';
import { sumKobo } from '@/shared/money/kobo';
import { MESSAGES } from '@/shared/messages/en';
import { Avatar } from '@/shared/ui/avatar';
import { ChevronRightIcon, PeopleIcon } from '@/shared/ui/icons';
import { ListGroup, RowContent } from '@/shared/ui/list';
import { PageHeader } from '@/shared/ui/page-header';

function status(customer: Customer): { text: string; tone: string } {
  if (customer.balanceKobo > 0) return { text: MESSAGES.customers.owes, tone: 'text-credit' };
  if (customer.balanceKobo < 0) return { text: MESSAGES.customers.inCredit, tone: 'text-muted' };
  return { text: MESSAGES.customers.settled, tone: 'text-muted' };
}

function CustomerRow({ customer }: { readonly customer: Customer }) {
  const { text, tone } = status(customer);
  return (
    <li>
      <Link
        to="/customers/$customerId"
        params={{ customerId: customer.id }}
        className="press flex items-center pr-3 active:bg-surface-2"
        data-testid="customer-row"
      >
        <RowContent
          leading={<Avatar name={customer.name} />}
          title={customer.name}
          subtitle={<span className={tone}>{text}</span>}
          trailing={
            customer.balanceKobo === 0 ? undefined : (
              <span className={`amount text-[16px] font-semibold ${tone}`}>{formatNaira(Math.abs(customer.balanceKobo) as Customer['balanceKobo'])}</span>
            )
          }
        />
        <ChevronRightIcon width={18} height={18} className="shrink-0 text-muted" />
      </Link>
    </li>
  );
}

/** F3: who owes the trader, largest debts first. Plain words, never colour alone. */
export function DebtBookPage() {
  const { customers, loading } = useCustomers();
  const debtors = customers.filter((c) => c.balanceKobo > 0);
  const owed = sumKobo(debtors.map((c) => c.balanceKobo));
  return (
    <div className="flex flex-col gap-5">
      <PageHeader title={MESSAGES.customers.title} />
      <section aria-labelledby="owed-label" className="rounded-[24px] border border-border bg-surface p-5 shadow-card">
        <p id="owed-label" className="text-[15px] font-medium text-muted">
          {MESSAGES.customers.totalOwed}
        </p>
        <p className="amount mt-1 text-[36px] font-bold leading-none tracking-[-0.03em] text-credit" data-testid="total-owed">
          {formatNaira(owed)}
        </p>
        <p className="mt-2 text-[15px] text-muted">{MESSAGES.customers.fromCustomers(debtors.length)}</p>
      </section>
      {!loading && customers.length === 0 && (
        <div className="flex flex-col items-center gap-3 rounded-[24px] border border-dashed border-border p-8 text-center">
          <span className="flex h-14 w-14 items-center justify-center rounded-full bg-surface-2 text-muted">
            <PeopleIcon />
          </span>
          <p className="text-muted">{MESSAGES.customers.empty}</p>
        </div>
      )}
      {customers.length > 0 && (
        <ListGroup aria-label={MESSAGES.customers.title}>
          {customers.map((customer) => (
            <CustomerRow key={customer.id} customer={customer} />
          ))}
        </ListGroup>
      )}
    </div>
  );
}

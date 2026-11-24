import { screen, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { Draft } from '@/inference/domain/draft';
import { newUlid } from '@/shared/ids/ulid';
import { createFakeServices } from '@/test/fake-services';
import { renderScreen } from '@/test/render';
import { DebtBookPage } from './debt-book-page';

const base = { item: null, quantity: null, unit: null, paidNaira: null, confidence: 1 } as const;

async function seed(services: ReturnType<typeof createFakeServices>['services'], drafts: Draft[]) {
  const source = { inference: 'manual', reason: 'MANUAL', promptVersion: null } as const;
  const result = await services.ledger.confirmDrafts(
    drafts.map((draft) => ({ draft, journalId: newUlid(), idempotencyKey: newUlid(), occurredAt: new Date(), source })),
  );
  if (!result.ok) throw new Error(result.error);
}

describe('Debt book (F3)', () => {
  it('lists who owes money, largest first, with the total owed', async () => {
    const fake = createFakeServices();
    await seed(fake.services, [
      { ...base, kind: 'credit_sale', totalNaira: 5000, paidNaira: 0, customerName: 'Ada' },
      { ...base, kind: 'credit_sale', totalNaira: 45000, paidNaira: 35000, customerName: 'Mama Bisi' },
      { ...base, kind: 'credit_sale', totalNaira: 3000, paidNaira: 0, customerName: 'Tunde' },
      { ...base, kind: 'repayment', totalNaira: 3000, customerName: 'tunde' },
    ]);
    renderScreen(DebtBookPage, { services: fake.services, path: '/customers' });

    await waitFor(() => expect(screen.getByTestId('total-owed')).toHaveTextContent('₦15,000'));
    const rows = screen.getAllByTestId('customer-row');
    expect(rows.map((row) => row.textContent)).toEqual(['MBMama Bisiowes you₦10,000', 'AAdaowes you₦5,000', 'TTundeAll paid']);
  });

  it('explains the empty state', async () => {
    renderScreen(DebtBookPage, { services: createFakeServices().services, path: '/customers' });
    expect(await screen.findByText('Nobody owes you money. When you sell on credit, the customer shows here.')).toBeInTheDocument();
  });
});

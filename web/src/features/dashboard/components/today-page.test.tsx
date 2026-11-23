import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { createFakeServices } from '@/test/fake-services';
import { renderScreen } from '@/test/render';
import { TodayPage } from './today-page';

const SALE = JSON.stringify({
  drafts: [{ kind: 'credit_sale', item: 'rice', quantity: 3, unit: 'bag', totalNaira: 45000, paidNaira: 35000, customerName: 'Mama Bisi', confidence: 0.9 }],
  needsClarification: null,
});

describe('Today screen (F1, F5, F6)', () => {
  it('turns a typed sentence into a draft, saves it, and updates the totals', async () => {
    const user = userEvent.setup();
    const fake = createFakeServices({ script: { device: [{ text: SALE }] } });
    renderScreen(TodayPage, { services: fake.services });

    await user.type(await screen.findByLabelText('What happened?'), 'sold 3 bags of rice 45k, Mama Bisi paid 35k');
    await user.click(screen.getByRole('button', { name: 'Read it' }));

    const review = await screen.findByRole('article', { name: 'Check and save' });
    expect(within(review).getByTestId('inference-badge')).toHaveTextContent('On device');
    expect(within(review).getByTestId('draft')).toHaveTextContent('Mama Bisi still owes ₦10,000');

    await user.click(within(review).getByRole('button', { name: 'Save entry' }));
    await waitFor(() => expect(screen.getByTestId('total-sales')).toHaveTextContent('₦45,000'));
    expect(screen.getByTestId('total-profit')).toHaveTextContent('₦45,000');
    expect(fake.ledger.journals.size).toBe(1);
    expect(screen.getByTestId('pending-sync')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('article', { name: 'Check and save' })).not.toBeInTheDocument());
  });

  it('blocks saving an edited draft that breaks a ledger rule, and explains why', async () => {
    const user = userEvent.setup();
    const fake = createFakeServices({ script: { device: [{ text: SALE }] } });
    renderScreen(TodayPage, { services: fake.services });
    await user.type(await screen.findByLabelText('What happened?'), 'sold rice');
    await user.click(screen.getByRole('button', { name: 'Read it' }));
    const review = await screen.findByRole('article', { name: 'Check and save' });

    await user.click(within(review).getByRole('button', { name: 'Edit' }));
    const paid = within(review).getByLabelText('Paid now (₦)');
    await user.clear(paid);
    await user.type(paid, '50000');

    expect(within(review).getByRole('alert')).toHaveTextContent('Paid now cannot be more than the total.');
    expect(within(review).getByRole('button', { name: 'Save entry' })).toBeDisabled();
  });

  it('shows a clarifying question when the note has no amount', async () => {
    const user = userEvent.setup();
    const fake = createFakeServices({ script: { device: [{ text: JSON.stringify({ drafts: [], needsClarification: 'How much was the rice?' }) }] } });
    renderScreen(TodayPage, { services: fake.services });
    await user.type(await screen.findByLabelText('What happened?'), 'sold rice');
    await user.click(screen.getByRole('button', { name: 'Read it' }));
    expect(await screen.findByText('How much was the rice?')).toBeInTheDocument();
  });

  it('queues the note when offline without a device model', async () => {
    const user = userEvent.setup();
    const fake = createFakeServices({ env: { online: false, deviceModel: 'unavailable' } });
    renderScreen(TodayPage, { services: fake.services });
    await user.type(await screen.findByLabelText('What happened?'), 'sold rice 5k');
    await user.click(screen.getByRole('button', { name: 'Read it' }));
    const job = await screen.findByTestId('job');
    // A new job is briefly "queued" before its first run, so wait for the final badge.
    await waitFor(() => expect(within(job).getByTestId('inference-badge')).toHaveTextContent('Waiting for network'));
    expect(job).toHaveAttribute('data-state', 'queued');
    expect(fake.calls).toHaveLength(0);
  });
});

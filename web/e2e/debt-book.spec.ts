import { expect, test } from '@playwright/test';
import { openApp, recordNote } from './helpers';

test('journey 3: Pidgin goes to the cloud, the debt book updates, and Undo reverses it (F3, F4, F6)', async ({ page }) => {
  await openApp(page);
  await page.getByLabel('What happened?').fill('Mama Bisi buy rice 45k, Mama Bisi owes 10k, she don pay the rest');
  await page.getByRole('button', { name: 'Send', exact: true }).click();
  const badge = page.getByTestId('inference-badge').first();
  await expect(badge).toHaveAttribute('data-source', 'in_cloud');
  await expect(badge).toHaveAttribute('data-reason', 'LANGUAGE');
  await page.getByRole('button', { name: /^Save/ }).click();

  await page.getByRole('link', { name: 'Debts' }).click();
  await expect(page.getByTestId('total-owed')).toHaveText('₦10,000');
  await page.getByTestId('customer-row').filter({ hasText: 'Mama Bisi' }).click();
  await expect(page.getByTestId('customer-balance')).toHaveText('₦10,000');

  // Rows show a summary; tapping one reveals the source and the Undo / Fix actions.
  const row = page.getByTestId('journal-row').first();
  await row.getByRole('button', { expanded: false }).click();
  await row.getByRole('button', { name: 'Undo' }).click();
  await expect(page.getByTestId('customer-balance')).toHaveText('₦0');
  await expect(page.getByTestId('journal-row')).toHaveCount(2);
  await expect(page.getByTestId('journal-row').filter({ hasText: 'Undone' })).toHaveCount(1);
});

test('journey 3b: recording a repayment by hand reduces the balance', async ({ page }) => {
  await openApp(page);
  await recordNote(page, 'sold garri 3000, Ada owes 3000');
  await page.getByRole('link', { name: 'Debts' }).click();
  await page.getByTestId('customer-row').filter({ hasText: 'Ada' }).click();
  await page.getByLabel('Amount paid (₦)').fill('1000');
  await page.getByRole('button', { name: 'Record payment' }).click();
  await expect(page.getByTestId('customer-balance')).toHaveText('₦2,000');
});

test('journey 3c: the "Entry saved" toast can undo the save straight away', async ({ page }) => {
  await openApp(page);
  await recordNote(page, 'sold 2 crates of eggs 9000');
  await expect(page.getByTestId('total-sales')).toHaveText('₦9,000');
  await page.getByRole('status').getByRole('button', { name: 'Undo' }).click();
  await expect(page.getByTestId('total-sales')).toHaveText('₦0');
  await expect(page.getByTestId('journal-row').filter({ hasText: 'Undone' })).toHaveCount(1);
});

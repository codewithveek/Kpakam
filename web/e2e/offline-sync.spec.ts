import { expect, test } from '@playwright/test';
import { openApp, recordNote } from './helpers';

test('journey 1: record a sale offline, on device, then it syncs on reconnect (F1, F2, F6)', async ({ page, context }) => {
  await openApp(page);
  await context.setOffline(true);
  await expect(page.getByTestId('network-status')).toHaveAttribute('data-online', 'false');

  await page.getByLabel('What happened?').fill('sold 3 bags of rice 45k');
  await page.getByRole('button', { name: 'Read it' }).click();
  const badge = page.getByTestId('inference-badge').first();
  await expect(badge).toHaveAttribute('data-source', 'on_device');
  await expect(badge).toHaveAttribute('data-reason', 'DEFAULT_DEVICE');
  await page.getByRole('button', { name: /^Save/ }).click();

  await expect(page.getByTestId('total-sales')).toHaveText('₦45,000');
  await expect(page.getByTestId('pending-sync')).toBeVisible();

  await context.setOffline(false);
  await expect(page.getByTestId('pending-sync')).toHaveCount(0, { timeout: 10_000 });
  await expect(page.getByTestId('journal-row')).toHaveCount(1);
  await expect(page.getByTestId('calls-avoided')).toHaveText('1 note read free on your phone this week');
});

test('journey 1b: a reload keeps offline entries and does not duplicate them', async ({ page, context }) => {
  await openApp(page);
  await recordNote(page, 'sold 2 tins of milk 2k');
  await page.evaluate(() => navigator.serviceWorker.ready.then(() => true));
  await context.setOffline(true);
  await page.reload();
  await expect(page.getByTestId('journal-row')).toHaveCount(1);
  await context.setOffline(false);
  await expect(page.getByTestId('total-sales')).toHaveText('₦2,000');
});

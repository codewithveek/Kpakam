import { expect, test } from '@playwright/test';
import { openApp } from './helpers';

// A 1×1 PNG: enough for the on-device compressor and the scripted model.
const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64');

test('journey 2: data-saver holds a receipt photo until Send now (F7, S1)', async ({ page }) => {
  await openApp(page, '/settings');
  const toggle = page.getByRole('switch', { name: 'Data saver' });
  await toggle.click();
  await expect(toggle).toHaveAttribute('aria-checked', 'true');

  await page.getByRole('link', { name: 'Today' }).click();
  await page.locator('input[type="file"]').setInputFiles({ name: 'receipt.png', mimeType: 'image/png', buffer: PNG });

  const job = page.getByTestId('job').first();
  await expect(job).toHaveAttribute('data-state', 'queued');
  await expect(job.getByTestId('inference-badge')).toContainText('Waiting for you to tap Send now');
  await job.getByRole('button', { name: /Send now/ }).click();

  const badge = page.getByTestId('inference-badge').first();
  await expect(badge).toHaveAttribute('data-source', 'in_cloud');
  await expect(badge).toHaveAttribute('data-reason', 'IMAGE_INPUT');
  await page.getByRole('button', { name: /^Save/ }).click();
  await expect(page.getByTestId('total-expenses')).toHaveText('₦12,500');
});

test('journey 2b: typed entries still run with data-saver on (only large jobs wait)', async ({ page }) => {
  await openApp(page, '/settings');
  await page.getByRole('switch', { name: 'Data saver' }).click();
  await page.getByRole('link', { name: 'Today' }).click();
  await page.getByLabel('What happened?').fill('paid transport 1500');
  await page.getByRole('button', { name: 'Read it' }).click();
  await expect(page.getByTestId('draft')).toHaveCount(1);
});

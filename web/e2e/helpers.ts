import { expect, type Page } from '@playwright/test';

/** Opens the app and waits until a trader is signed in and the entry box is ready. */
export async function openApp(page: Page, path = '/'): Promise<void> {
  await page.goto(path);
  // Every screen has exactly one page heading once the trader is signed in.
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible({ timeout: 30_000 });
}

/** Types a note, reads it, and saves every draft. */
export async function recordNote(page: Page, note: string): Promise<void> {
  await page.getByLabel('What happened?').fill(note);
  await page.getByRole('button', { name: 'Read it' }).click();
  await expect(page.getByTestId('draft').first()).toBeVisible();
  await page.getByRole('button', { name: /^Save/ }).click();
  await expect(page.getByTestId('draft')).toHaveCount(0);
}

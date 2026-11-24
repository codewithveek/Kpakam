import { defineConfig, devices } from '@playwright/test';

const PORT = 5174;

export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env['CI'] === undefined ? 0 : 1,
  reporter: process.env['CI'] === undefined ? 'list' : [['list'], ['html', { open: 'never' }]],
  use: { baseURL: `http://localhost:${PORT}`, trace: 'off', ...devices['Pixel 7'] },
  projects: [{ name: 'chrome-mobile', use: { browserName: 'chromium' } }],
  webServer: [
    {
      command: 'npx firebase emulators:start --only auth,firestore --project demo-kpakam-e2e --config ../firebase.e2e.json',
      url: 'http://127.0.0.1:8180',
      reuseExistingServer: process.env['CI'] === undefined,
      timeout: 120_000,
    },
    {
      // A production build, so the service worker precaches the shell (offline reload works).
      command: `npx vite build --mode e2e && npx vite preview --mode e2e --port ${PORT} --strictPort`,
      url: `http://localhost:${PORT}`,
      reuseExistingServer: process.env['CI'] === undefined,
      timeout: 120_000,
    },
  ],
});

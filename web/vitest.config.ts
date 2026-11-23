import { fileURLToPath, URL } from 'node:url';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

const src = fileURLToPath(new URL('./src', import.meta.url));
const contract = fileURLToPath(new URL('../packages/contract', import.meta.url));

export default defineConfig({
  plugins: [react()],
  resolve: { alias: { '@': src, '@contract': contract } },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    coverage: {
      provider: 'v8',
      include: ['src/**/domain/**/*.ts', 'src/inference/**/*.ts', 'src/shared/money/**/*.ts'],
      exclude: ['**/*.test.ts', '**/index.ts', 'src/inference/clients/firebase-*.ts'],
      thresholds: {
        'src/**/domain/**/*.ts': { lines: 95, branches: 95 },
      },
    },
  },
});

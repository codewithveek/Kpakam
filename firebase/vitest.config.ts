import { defineConfig } from 'vitest/config';

// The rules tests share one Firestore emulator and clear it between tests,
// so test files must run one at a time.
export default defineConfig({
  test: {
    include: ['tests/**/*.test.ts'],
    environment: 'node',
    fileParallelism: false,
    testTimeout: 20_000,
    hookTimeout: 60_000,
  },
});

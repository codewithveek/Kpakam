import js from '@eslint/js';
import jsxA11y from 'eslint-plugin-jsx-a11y';
import reactHooks from 'eslint-plugin-react-hooks';
import globals from 'globals';
import tseslint from 'typescript-eslint';

/** Size and complexity limits from the PRD "Engineering and coding standards". */
const LIMITS = {
  complexity: ['error', 10],
  'max-depth': ['error', 3],
  'max-lines': ['error', { max: 300, skipBlankLines: true, skipComments: true }],
  'max-params': ['error', 2],
};

export default tseslint.config(
  { ignores: ['dist', 'coverage', 'playwright-report', 'test-results', 'dev-dist'] },
  js.configs.recommended,
  ...tseslint.configs.strictTypeChecked,
  {
    languageOptions: {
      globals: globals.browser,
      parserOptions: { projectService: true, tsconfigRootDir: import.meta.dirname },
    },
    plugins: { 'react-hooks': reactHooks, 'jsx-a11y': jsxA11y },
    rules: {
      ...reactHooks.configs.recommended.rules,
      ...jsxA11y.flatConfigs.recommended.rules,
      ...LIMITS,
      'max-lines-per-function': ['error', { max: 40, skipBlankLines: true, skipComments: true, IIFEs: true }],
      'no-restricted-exports': ['error', { restrictDefaultExports: { direct: true, named: true, defaultFrom: true, namedFrom: true, namespaceFrom: true } }],
      '@typescript-eslint/no-non-null-assertion': 'error',
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/ban-ts-comment': ['error', { 'ts-ignore': true, 'ts-expect-error': 'allow-with-description' }],
      '@typescript-eslint/switch-exhaustiveness-check': 'error',
      '@typescript-eslint/restrict-template-expressions': ['error', { allowNumber: true }],
      '@typescript-eslint/no-confusing-void-expression': ['error', { ignoreArrowShorthand: true }],
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['firebase/*', '@firebase/*'],
              allowTypeImports: true,
              message: 'Only platform adapters and repositories may import Firebase. Depend on our interfaces instead.',
            },
          ],
        },
      ],
    },
  },
  {
    // Components may be up to 150 lines (PRD); plain functions stay at 40.
    files: ['**/*.tsx'],
    rules: { 'max-lines-per-function': ['error', { max: 150, skipBlankLines: true, skipComments: true }] },
  },
  {
    // The layers that are allowed to touch the SDK.
    files: [
      'scripts/**',
      'src/platform/**',
      'src/**/repository/**',
      'src/inference/ai-event-repository.ts',
      'src/inference/clients/firebase-*.ts',
      'src/app/vitals.ts',
      'src/app/services.ts',
    ],
    rules: { 'no-restricted-imports': 'off' },
  },
  {
    files: ['**/*.test.ts', '**/*.test.tsx', 'e2e/**', 'src/test/**'],
    rules: {
      'max-lines-per-function': 'off',
      'max-lines': 'off',
      'max-params': 'off',
      '@typescript-eslint/no-unsafe-assignment': 'off',
      '@typescript-eslint/no-unsafe-member-access': 'off',
    },
  },
  {
    files: ['*.config.ts', '*.config.js', 'scripts/**'],
    languageOptions: { globals: globals.node },
    rules: { 'no-restricted-exports': 'off', 'max-lines-per-function': 'off' },
  },
  { files: ['scripts/**/*.mjs', '*.config.js'], ...tseslint.configs.disableTypeChecked },
);

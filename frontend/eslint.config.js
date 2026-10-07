import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import { defineConfig, globalIgnores } from 'eslint/config'

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      // Type-aware rules: they also catch `any` coming from libraries
      // (e.g. JSON.parse), not only the `any` we would write ourselves.
      tseslint.configs.recommendedTypeChecked,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      globals: globals.browser,
      parserOptions: {
        project: ['./tsconfig.app.json', './tsconfig.node.json'],
        tsconfigRootDir: import.meta.dirname,
      },
    },
    linterOptions: {
      // `eslint-disable` comments are ignored (and reported): a rule cannot be
      // switched off from inside the code.
      noInlineConfig: true,
    },
    rules: {
      // No `any` in our code: incoming data is `unknown` + type guards.
      '@typescript-eslint/no-explicit-any': 'error',
      // A missing effect dependency means a stale value or a leaked subscription.
      'react-hooks/exhaustive-deps': 'error',
    },
  },
])

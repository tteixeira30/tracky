import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'

export default [
  // android/ contém o bundle web copiado pelo Capacitor (git-ignored, mas
  // presente localmente) — não é código-fonte para lint
  { ignores: ['dist', 'coverage', 'node_modules', 'android'] },
  js.configs.recommended,
  reactHooks.configs.flat.recommended,
  reactRefresh.configs.vite,
  {
    files: ['**/*.{js,mjs,jsx}'],
    languageOptions: {
      ecmaVersion: 2023,
      globals: { ...globals.browser, ...globals.node },
      parserOptions: {
        ecmaVersion: 'latest',
        ecmaFeatures: { jsx: true },
        sourceType: 'module',
      },
    },
    rules: {
      // componentes/constantes em maiúscula não contam como "não usados"
      'no-unused-vars': ['error', { varsIgnorePattern: '^[A-Z_]' }],
      // padrões existentes no projeto (contextos exportam hook + provider;
      // setState síncrono em effects de carregamento) — sinalizar sem falhar o CI
      'react-refresh/only-export-components': 'warn',
      'react-hooks/set-state-in-effect': 'warn',
    },
  },
  {
    // ficheiros de teste: globals do Vitest
    files: ['src/test/**'],
    languageOptions: {
      globals: { ...globals.browser, ...globals.node, ...globals.vitest },
    },
  },
]

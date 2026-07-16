import { defineConfig, devices } from '@playwright/test'

/**
 * Testes E2E contra a stack completa em Docker (nginx + backend + Postgres):
 *   docker compose up -d --build
 *   npx playwright test
 *
 * workers: 1 — os testes partilham o mesmo backend/BD; cada teste cria o seu
 * próprio utilizador (email único), por isso nunca tocam em dados existentes.
 */
export default defineConfig({
  testDir: './tests',
  timeout: 30_000,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:3000',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  reporter: process.env.CI
    ? [['github'], ['html', { open: 'never' }]]
    : [['list'], ['html', { open: 'never' }]],
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})

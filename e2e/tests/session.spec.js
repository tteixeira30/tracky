import { expect, test } from '@playwright/test'
import { registerViaUi, sidebarTab } from './helpers.js'

test.describe('sessão e rendimento', () => {
  test('a sessão sobrevive a um reload da página', async ({ page }) => {
    const { name } = await registerViaUi(page, { name: 'Persistente Silva' })

    await page.reload()

    // continua autenticado — o token em localStorage revalida via /auth/me
    await expect(sidebarTab(page, 'Painel')).toBeVisible()
    await expect(page.locator('.user-chip')).toContainText(name)
  })

  test('depois de logout o reload não recupera a sessão', async ({ page }) => {
    await registerViaUi(page)
    await page.getByRole('button', { name: 'Terminar sessão' }).click()
    await expect(page.getByRole('button', { name: 'Entrar' })).toBeVisible()

    await page.reload()

    await expect(page.getByRole('button', { name: 'Entrar' })).toBeVisible()
  })

  test('definir o rendimento mensal reflete-se na página', async ({ page }) => {
    await registerViaUi(page)
    await sidebarTab(page, 'Rendimento').click()

    await page.getByRole('button', { name: 'Editar' }).first().click()
    const dialog = page.getByRole('dialog')
    await dialog.locator('input[type="number"]').fill('2500')
    await dialog.getByRole('button', { name: 'Guardar' }).click()

    // valor formatado em EUR visível na página (2500 → "2500,00 €" com NBSP)
    await expect(page.getByText(/2500,00\s*€/).first()).toBeVisible()
  })
})

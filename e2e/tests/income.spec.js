import { expect, test } from '@playwright/test'
import { registerViaUi, sidebarTab } from './helpers.js'

test.describe('rendimento', () => {
  test('criar uma categoria de rendimento e vê-la na lista', async ({ page }) => {
    await registerViaUi(page)

    await sidebarTab(page, 'Rendimento').click()
    await page.getByRole('button', { name: 'Criar categoria' }).first().click()

    await expect(page.getByText('Nova categoria')).toBeVisible()
    await page.getByPlaceholder('Ex: Poupança, Renda…').fill('Poupança E2E')
    // por omissão a categoria é por percentagem
    await page.getByPlaceholder('Ex: 30').fill('20')
    await page.getByRole('button', { name: 'Adicionar' }).click()

    // aparece na lista de categorias (o toast também contém o nome — scoped ao título da linha)
    await expect(page.locator('.row-title', { hasText: 'Poupança E2E' })).toBeVisible()
  })
})

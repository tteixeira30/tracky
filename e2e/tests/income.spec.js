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

  test('categoria por valor fixo aparece marcada como "Valor fixo"', async ({ page }) => {
    await registerViaUi(page)

    await sidebarTab(page, 'Rendimento').click()
    await page.getByRole('button', { name: 'Criar categoria' }).first().click()

    const dialog = page.getByRole('dialog')
    await dialog.getByPlaceholder('Ex: Poupança, Renda…').fill('Renda Fixa E2E')
    await dialog.getByRole('button', { name: 'Valor fixo' }).click()
    await dialog.getByPlaceholder('Ex: 400').fill('450')
    await dialog.getByRole('button', { name: 'Adicionar' }).click()

    const row = page.locator('tr', { has: page.locator('.row-title', { hasText: 'Renda Fixa E2E' }) })
    await expect(row).toBeVisible()
    await expect(row.locator('.type-chip')).toHaveText('Valor fixo')
  })

  test('eliminar uma categoria remove-a da lista', async ({ page }) => {
    await registerViaUi(page)

    await sidebarTab(page, 'Rendimento').click()
    await page.getByRole('button', { name: 'Criar categoria' }).first().click()
    const dialog = page.getByRole('dialog')
    await dialog.getByPlaceholder('Ex: Poupança, Renda…').fill('Temporária E2E')
    await dialog.getByPlaceholder('Ex: 30').fill('15')
    await dialog.getByRole('button', { name: 'Adicionar' }).click()
    await expect(page.locator('.row-title', { hasText: 'Temporária E2E' })).toBeVisible()

    await page.getByRole('button', { name: 'Remover' }).first().click()
    // ConfirmDialog — botão de confirmação é o .btn.danger
    await page.locator('.btn.danger').click()

    await expect(page.locator('.row-title', { hasText: 'Temporária E2E' })).toHaveCount(0)
  })
})

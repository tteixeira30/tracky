import { expect, test } from '@playwright/test'
import { registerViaUi, sidebarTab } from './helpers.js'

/**
 * Usa sempre o tipo "Outro" (investimento manual, sem símbolo) — não depende
 * de cotações do Yahoo/CoinGecko, por isso é 100% determinístico em CI.
 */
async function createManualInvestment(page, { name, value, gain = '0' }) {
  await page.getByRole('button', { name: 'Novo investimento' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByPlaceholder('Ex: MSCI World').fill(name)
  // tipo "Outro" via dropdown acessível
  await dialog.locator('.dd-trigger').click()
  await page.getByRole('option', { name: 'Outro' }).click()
  await dialog.getByPlaceholder('Ex: 1500').fill(value)
  await dialog.getByPlaceholder('Ex: 12.5 ou -8').fill(gain)
  await dialog.getByRole('button', { name: 'Adicionar' }).click()
}

test.describe('investimentos (manuais, sem cotação)', () => {
  test('criar um investimento manual e vê-lo na tabela como "manual"', async ({ page }) => {
    await registerViaUi(page)
    await sidebarTab(page, 'Investimentos').click()

    await createManualInvestment(page, { name: 'PPR Offline', value: '1000' })

    const row = page.locator('tr', { has: page.locator('.row-title', { hasText: 'PPR Offline' }) })
    await expect(row).toBeVisible()
    await expect(row.locator('.badge')).toHaveText('manual')
    await expect(row.locator('.type-chip')).toHaveText('Outro')
  })

  test('% de ganho gera valor investido e ganho coerentes', async ({ page }) => {
    await registerViaUi(page)
    await sidebarTab(page, 'Investimentos').click()

    // 1100€ com +10% de ganho → investido = 1000€
    await createManualInvestment(page, { name: 'Com Ganho', value: '1100', gain: '10' })

    const row = page.locator('tr', { has: page.locator('.row-title', { hasText: 'Com Ganho' }) })
    await expect(row).toBeVisible()
    await expect(row.getByText('+10.00%')).toBeVisible()
  })

  test('editar o nome de um investimento', async ({ page }) => {
    await registerViaUi(page)
    await sidebarTab(page, 'Investimentos').click()
    await createManualInvestment(page, { name: 'Nome Antigo', value: '500' })
    await expect(page.locator('.row-title', { hasText: 'Nome Antigo' })).toBeVisible()

    await page.getByRole('button', { name: 'Editar' }).first().click()
    const dialog = page.getByRole('dialog')
    // o campo do nome no modal de edição não tem placeholder — é o primeiro input
    await dialog.locator('input').first().fill('Nome Novo')
    await dialog.getByRole('button', { name: 'Guardar' }).click()

    await expect(page.locator('.row-title', { hasText: 'Nome Novo' })).toBeVisible()
    await expect(page.locator('.row-title', { hasText: 'Nome Antigo' })).toHaveCount(0)
  })

  test('eliminar um investimento remove-o da tabela', async ({ page }) => {
    await registerViaUi(page)
    await sidebarTab(page, 'Investimentos').click()
    await createManualInvestment(page, { name: 'A Eliminar', value: '100' })
    await expect(page.locator('.row-title', { hasText: 'A Eliminar' })).toBeVisible()

    await page.getByRole('button', { name: 'Eliminar' }).first().click()
    await page.locator('.btn.danger', { hasText: 'Eliminar' }).click()

    await expect(page.locator('.row-title', { hasText: 'A Eliminar' })).toHaveCount(0)
  })
})

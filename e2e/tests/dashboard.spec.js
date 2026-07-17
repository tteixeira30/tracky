import { expect, test } from '@playwright/test'
import { registerViaUi, sidebarTab } from './helpers.js'

/** Cria um investimento manual (tipo "Outro", sem cotação — determinístico). */
async function createManualInvestment(page, { name, value }) {
  await sidebarTab(page, 'Investimentos').click()
  await page.getByRole('button', { name: 'Novo investimento' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByPlaceholder('Ex: MSCI World').fill(name)
  await dialog.locator('.dd-trigger').click()
  await page.getByRole('option', { name: 'Outro' }).click()
  await dialog.getByPlaceholder('Ex: 1500').fill(value)
  await dialog.getByPlaceholder('Ex: 12.5 ou -8').fill('0')
  await dialog.getByRole('button', { name: 'Adicionar' }).click()
  await expect(page.locator('.row-title', { hasText: name })).toBeVisible()
}

test.describe('painel (dashboard) — agregação', () => {
  test('conta nova mostra património zero e sugere adicionar dados', async ({ page }) => {
    await registerViaUi(page)
    // já está no Painel após o registo
    await expect(page.locator('.hero-value')).toHaveText(/0,00\s*€/)
    await expect(page.getByText(/Adiciona investimentos ou objetivos/)).toBeVisible()
  })

  test('património líquido = investimentos + poupança', async ({ page }) => {
    await registerViaUi(page)

    // rendimento do mês
    await sidebarTab(page, 'Rendimento').click()
    await page.getByRole('button', { name: 'Editar' }).first().click()
    let dialog = page.getByRole('dialog')
    await dialog.locator('input[type="number"]').fill('2000')
    await dialog.getByRole('button', { name: 'Guardar' }).click()
    await expect(page.getByText(/2000,00\s*€/).first()).toBeVisible()

    // investimento manual de 1000€
    await createManualInvestment(page, { name: 'Carteira Painel', value: '1000' })

    // objetivo com 500€ já poupados
    await sidebarTab(page, 'Objetivos').click()
    await page.getByRole('button', { name: 'Novo objetivo' }).click()
    dialog = page.getByRole('dialog')
    await dialog.getByPlaceholder('Ex: Fundo de emergência').fill('Meta Painel')
    await dialog.getByPlaceholder('Ex: 10000').fill('5000')
    await dialog.getByPlaceholder('Ex: 300').fill('100')
    await dialog.getByPlaceholder('0', { exact: true }).fill('500') // já poupado
    await dialog.getByRole('button', { name: 'Criar objetivo' }).click()
    await expect(page.locator('.goal-title', { hasText: 'Meta Painel' })).toBeVisible()

    // Painel: 1000 (investido) + 500 (poupado) = 1500 de património líquido
    await sidebarTab(page, 'Painel').click()
    await expect(page.locator('.hero-value')).toHaveText(/1500,00\s*€/)

    const investKpi = page.locator('.kpi-card', { hasText: 'Valor investido' })
    await expect(investKpi.locator('.kpi-value')).toHaveText(/1000,00\s*€/)

    const savedKpi = page.locator('.kpi-card', { hasText: 'Poupado em objetivos' })
    await expect(savedKpi.locator('.kpi-value')).toHaveText(/500,00\s*€/)

    const incomeKpi = page.locator('.kpi-card', { hasText: 'Rendimento do mês' })
    await expect(incomeKpi.locator('.kpi-value')).toHaveText(/2000,00\s*€/)
  })
})

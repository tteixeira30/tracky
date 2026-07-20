import { expect, test } from '@playwright/test'
import { registerViaUi, sidebarTab } from './helpers.js'

/**
 * Percurso completo de um utilizador novo: passa por todas as áreas principais
 * (rendimento → despesas + import → investimento → objetivo → calendário) e
 * confirma no fim que o Painel agrega tudo. Complementa os specs por-feature:
 * aqui interessa a integração entre elas, não cada CRUD isolado.
 *
 * Tudo determinístico — investimento manual (tipo "Outro", sem cotação) e
 * movimentos criados pelo próprio teste.
 */

function currentYm() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

test('percurso completo: rendimento, despesas, investimento e objetivo agregam no painel', async ({ page }) => {
  await registerViaUi(page, { name: 'Percurso Completo' })

  // 1) Rendimento mensal
  await sidebarTab(page, 'Rendimento').click()
  await page.getByRole('button', { name: 'Editar' }).first().click()
  let dialog = page.getByRole('dialog')
  await dialog.locator('input[type="number"]').fill('2000')
  await dialog.getByRole('button', { name: 'Guardar' }).click()
  await expect(page.getByText(/2000,00\s*€/).first()).toBeVisible()

  // 2) Despesas — criar conta e importar um extrato (dá saldo e movimentos)
  await sidebarTab(page, 'Despesas').click()
  await page.locator('.account-chip.add').click()
  dialog = page.getByRole('dialog')
  await dialog.getByPlaceholder('Ex: Santander').fill('Conta Principal')
  await dialog.getByPlaceholder(/Deixa em branco/).fill('3000')
  await dialog.getByRole('button', { name: 'Guardar' }).click()
  await expect(dialog).toBeHidden()

  const ym = currentYm()
  const csv = [
    'Data,Descrição,Montante',
    `${ym}-04,Continente,-60.00`,
    `${ym}-08,Salário,2000.00`,
  ].join('\n')
  await page.locator('.page-actions').getByRole('button', { name: 'Importar extrato' }).click()
  dialog = page.getByRole('dialog')
  await dialog.locator('input[type="file"]').setInputFiles({
    name: 'extrato.csv', mimeType: 'text/csv', buffer: Buffer.from(csv),
  })
  await expect(dialog.getByText(/2 movimento\(s\) prontos a importar/)).toBeVisible()
  await dialog.getByRole('button', { name: /Importar 2 movimento/ }).click()
  await expect(dialog).toBeHidden()
  await expect(page.locator('.event-row', { hasText: 'Continente' })).toBeVisible()

  // 3) Investimento manual de 1000€
  await sidebarTab(page, 'Investimentos').click()
  await page.getByRole('button', { name: 'Novo investimento' }).click()
  dialog = page.getByRole('dialog')
  await dialog.getByPlaceholder('Ex: MSCI World').fill('Carteira Longo Prazo')
  await dialog.locator('.dd-trigger').click()
  await page.getByRole('option', { name: 'Outro' }).click()
  await dialog.getByPlaceholder('Ex: 1500').fill('1000')
  await dialog.getByPlaceholder('Ex: 12.5 ou -8').fill('0')
  await dialog.getByRole('button', { name: 'Adicionar' }).click()
  await expect(page.locator('.row-title', { hasText: 'Carteira Longo Prazo' })).toBeVisible()

  // 4) Objetivo com 500€ já poupados
  await sidebarTab(page, 'Objetivos').click()
  await page.getByRole('button', { name: 'Novo objetivo' }).click()
  dialog = page.getByRole('dialog')
  await dialog.getByPlaceholder('Ex: Fundo de emergência').fill('Fundo de Emergência')
  await dialog.getByPlaceholder('Ex: 10000').fill('5000')
  await dialog.getByPlaceholder('Ex: 300').fill('200')
  await dialog.getByPlaceholder('0', { exact: true }).fill('500') // já poupado
  await dialog.getByRole('button', { name: 'Criar objetivo' }).click()
  await expect(page.locator('.goal-title', { hasText: 'Fundo de Emergência' })).toBeVisible()

  // 5) Painel agrega tudo: património = investido (1000) + poupado (500) = 1500
  await sidebarTab(page, 'Painel').click()
  await expect(page.locator('.hero-value')).toHaveText(/1500,00\s*€/)
  await expect(page.locator('.kpi-card', { hasText: 'Valor investido' }).locator('.kpi-value')).toHaveText(/1000,00\s*€/)
  await expect(page.locator('.kpi-card', { hasText: 'Poupado em objetivos' }).locator('.kpi-value')).toHaveText(/500,00\s*€/)
  await expect(page.locator('.kpi-card', { hasText: 'Rendimento do mês' }).locator('.kpi-value')).toHaveText(/2000,00\s*€/)
})

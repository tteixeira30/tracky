import { expect, test } from '@playwright/test'
import { registerViaUi, sidebarTab } from './helpers.js'

test.describe('calendário financeiro', () => {
  test('criar um evento mensal e vê-lo na lista de eventos', async ({ page }) => {
    await registerViaUi(page)
    await sidebarTab(page, 'Calendário').click()

    await page.getByRole('button', { name: 'Novo evento' }).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByPlaceholder('Ex: Salário, Renda, Netflix').fill('Renda E2E')
    // tipo por omissão: Saída · frequência por omissão: Mensal, dia 1
    await dialog.getByPlaceholder('0').fill('800')
    await dialog.getByRole('button', { name: 'Guardar' }).click()

    // aparece na lista de eventos (o nome também surge no toast — usar .first())
    await expect(page.getByText('Renda E2E').first()).toBeVisible()
  })

  // O saldo de partida da previsão vem da soma das contas bancárias (Despesas).
  async function criarContaComSaldo(page, saldo) {
    await sidebarTab(page, 'Despesas').click()
    await page.getByRole('button', { name: 'Criar conta' }).first().click()
    const dialog = page.getByRole('dialog')
    await dialog.getByPlaceholder('Ex: Santander').fill('Conta E2E')
    await dialog.getByPlaceholder(/Deixa em branco/).fill(saldo)
    await dialog.getByRole('button', { name: 'Guardar' }).click()
  }

  test('o saldo das contas ativa a previsão a 60 dias', async ({ page }) => {
    await registerViaUi(page)
    await criarContaComSaldo(page, '2500')

    await sidebarTab(page, 'Calendário').click()

    await expect(page.getByText(/previsto a 60 dias/)).toBeVisible()
  })

  test('evento de entrada mensal entra na previsão de saldo', async ({ page }) => {
    await registerViaUi(page)
    await criarContaComSaldo(page, '1000')

    await sidebarTab(page, 'Calendário').click()
    await expect(page.getByText(/previsto a 60 dias/)).toBeVisible()

    // evento de entrada (salário)
    await page.getByRole('button', { name: 'Novo evento' }).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByPlaceholder('Ex: Salário, Renda, Netflix').fill('Salário E2E')
    await dialog.getByRole('button', { name: 'Entrada' }).click()
    await dialog.getByPlaceholder('0').fill('2000')
    await dialog.getByRole('button', { name: 'Guardar' }).click()

    // a previsão inclui o novo movimento
    await expect(page.getByText('Salário E2E').first()).toBeVisible()
  })
})

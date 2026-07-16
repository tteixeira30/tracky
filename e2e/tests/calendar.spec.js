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

  test('definir o saldo atual ativa a previsão a 60 dias', async ({ page }) => {
    await registerViaUi(page)
    await sidebarTab(page, 'Calendário').click()

    await page.getByRole('button', { name: 'Saldo atual' }).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByPlaceholder('Ex: 2500').fill('2500')
    await dialog.getByRole('button', { name: 'Guardar' }).click()

    await expect(page.getByText(/previsto a 60 dias/)).toBeVisible()
  })

  test('evento de entrada mensal entra na previsão de saldo', async ({ page }) => {
    await registerViaUi(page)
    await sidebarTab(page, 'Calendário').click()

    // saldo de partida
    await page.getByRole('button', { name: 'Saldo atual' }).click()
    let dialog = page.getByRole('dialog')
    await dialog.getByPlaceholder('Ex: 2500').fill('1000')
    await dialog.getByRole('button', { name: 'Guardar' }).click()
    await expect(page.getByText(/previsto a 60 dias/)).toBeVisible()

    // evento de entrada (salário)
    await page.getByRole('button', { name: 'Novo evento' }).click()
    dialog = page.getByRole('dialog')
    await dialog.getByPlaceholder('Ex: Salário, Renda, Netflix').fill('Salário E2E')
    await dialog.getByRole('button', { name: 'Entrada' }).click()
    await dialog.getByPlaceholder('0').fill('2000')
    await dialog.getByRole('button', { name: 'Guardar' }).click()

    // a previsão inclui o novo movimento
    await expect(page.getByText('Salário E2E').first()).toBeVisible()
  })
})

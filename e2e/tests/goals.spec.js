import { expect, test } from '@playwright/test'
import { registerViaUi, sidebarTab } from './helpers.js'

test.describe('objetivos', () => {
  test('criar um objetivo e vê-lo na lista com progresso', async ({ page }) => {
    await registerViaUi(page)

    await sidebarTab(page, 'Objetivos').click()
    await page.getByRole('button', { name: 'Novo objetivo' }).click()

    await page.getByPlaceholder('Ex: Fundo de emergência').fill('Férias no Japão')
    await page.getByPlaceholder('Ex: 10000').fill('3000')
    await page.getByPlaceholder('Ex: 300').fill('250')
    // o botão de submissão do modal (há outro "Criar objetivo" no estado vazio)
    await page.getByRole('dialog').getByRole('button', { name: 'Criar objetivo' }).click()

    // o objetivo aparece na lista
    await expect(page.locator('.goal-title', { hasText: 'Férias no Japão' })).toBeVisible()
  })

  test('contribuir para um objetivo aumenta o poupado e o progresso', async ({ page }) => {
    await registerViaUi(page)

    await sidebarTab(page, 'Objetivos').click()
    await page.getByRole('button', { name: 'Novo objetivo' }).click()
    await page.getByPlaceholder('Ex: Fundo de emergência').fill('Fundo E2E')
    await page.getByPlaceholder('Ex: 10000').fill('1000')
    await page.getByPlaceholder('Ex: 300').fill('100')
    await page.getByRole('dialog').getByRole('button', { name: 'Criar objetivo' }).click()

    const card = page.locator('.goal-card', { has: page.locator('.goal-title', { hasText: 'Fundo E2E' }) })
    await expect(card).toBeVisible()
    // começa em 0%
    await expect(card.locator('.of')).toContainText('0.0%')

    // contribuir 200€ → 20% de 1000
    await card.locator('.goal-contribute input').fill('200')
    await card.getByRole('button', { name: 'Contribuir' }).click()

    await expect(card.locator('.big')).toHaveText(/200,00\s*€/)
    await expect(card.locator('.of')).toContainText('20.0%')
  })

  test('eliminar um objetivo remove-o da lista', async ({ page }) => {
    await registerViaUi(page)

    await sidebarTab(page, 'Objetivos').click()
    await page.getByRole('button', { name: 'Novo objetivo' }).click()
    await page.getByPlaceholder('Ex: Fundo de emergência').fill('Objetivo temporário')
    await page.getByPlaceholder('Ex: 10000').fill('100')
    await page.getByPlaceholder('Ex: 300').fill('10')
    await page.getByRole('dialog').getByRole('button', { name: 'Criar objetivo' }).click()
    await expect(page.locator('.goal-title', { hasText: 'Objetivo temporário' })).toBeVisible()

    await page.getByRole('button', { name: 'Eliminar' }).first().click()
    // diálogo de confirmação — o botão de confirmar é o .btn.danger
    await expect(page.getByText('Eliminar objetivo?')).toBeVisible()
    await page.locator('.btn.danger', { hasText: 'Eliminar' }).click()
    await expect(page.locator('.goal-title', { hasText: 'Objetivo temporário' })).toHaveCount(0)
  })
})

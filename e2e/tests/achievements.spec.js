import { expect, test } from '@playwright/test'
import { profileMenuAction, registerViaUi, sidebarTab } from './helpers.js'

test.describe('conquistas', () => {
  test('conta nova começa no nível 1 · Iniciante', async ({ page }) => {
    await registerViaUi(page)
    await profileMenuAction(page, 'Conquistas')

    await expect(page.getByRole('heading', { name: /Nível 1 · Iniciante/ })).toBeVisible()
    // nota: entrar na app cria a linha do mês atual → "Organizado" (10 pts) desbloqueia logo
    await expect(page.getByText('10 pontos')).toBeVisible()
  })

  test('criar o primeiro objetivo desbloqueia "Sonhador"', async ({ page }) => {
    await registerViaUi(page)

    // cria um objetivo
    await sidebarTab(page, 'Objetivos').click()
    await page.getByRole('button', { name: 'Novo objetivo' }).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByPlaceholder('Ex: Fundo de emergência').fill('Primeiro Objetivo')
    await dialog.getByPlaceholder('Ex: 10000').fill('1000')
    await dialog.getByPlaceholder('Ex: 300').fill('100')
    await dialog.getByRole('button', { name: 'Criar objetivo' }).click()
    await expect(page.locator('.goal-title', { hasText: 'Primeiro Objetivo' })).toBeVisible()

    // a conquista aparece e os pontos somam: Sonhador (10) + Organizado (10)
    await profileMenuAction(page, 'Conquistas')
    await expect(page.getByText('Sonhador')).toBeVisible()
    await expect(page.getByText('20 pontos')).toBeVisible()
  })
})

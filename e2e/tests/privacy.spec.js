import { expect, test } from '@playwright/test'
import { registerViaUi } from './helpers.js'

/**
 * "Ocultar valores" (modo privacidade) — troca todos os montantes por "••••" na
 * apresentação. É puramente do lado do cliente (fmtEur devolve a máscara) e o
 * estado persiste em localStorage, por isso é 100% determinístico.
 */

const MASK = '••••'

/** Abre o menu de perfil e clica no interruptor de ocultar/mostrar valores. */
async function toggleOcultar(page) {
  await page.getByRole('button', { name: 'Perfil e definições' }).click()
  // aria-label alterna entre "Esconder valores" e "Mostrar valores"
  await page.getByRole('switch', { name: /Esconder valores|Mostrar valores/ }).click()
  await page.keyboard.press('Escape') // fecha o menu para ver o painel
}

test.describe('modo privacidade (ocultar valores)', () => {
  test('ativar oculta os montantes do painel e desativar volta a mostrá-los', async ({ page }) => {
    await registerViaUi(page)

    // por omissão os valores estão visíveis (conta nova → 0,00 €)
    await expect(page.locator('.hero-value')).toHaveText(/0,00\s*€/)

    await toggleOcultar(page)
    await expect(page.locator('.hero-value')).toContainText(MASK)

    await toggleOcultar(page)
    await expect(page.locator('.hero-value')).toHaveText(/0,00\s*€/)
  })

  test('a preferência de ocultar sobrevive a um reload', async ({ page }) => {
    await registerViaUi(page)
    await toggleOcultar(page)
    await expect(page.locator('.hero-value')).toContainText(MASK)

    await page.reload()

    // continua mascarado após recarregar (persistido em localStorage)
    await expect(page.locator('.hero-value')).toContainText(MASK)
  })
})

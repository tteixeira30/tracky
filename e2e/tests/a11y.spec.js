import { expect, test } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { profileMenuAction, registerViaUi, sidebarTab } from './helpers.js'

/**
 * Bateria de acessibilidade (axe-core WCAG 2.0/2.1 A + AA) nas páginas
 * principais, em tema claro E escuro. Falha em qualquer violação de impacto
 * "serious"/"critical" (inclui contraste de cor); as de impacto menor não
 * bloqueiam mas ficam registadas no anexo do relatório.
 *
 * O tema resolve-se de `prefers-color-scheme` no primeiro arranque
 * (ThemeContext), por isso alterna-se via `colorScheme` do contexto Playwright.
 */

const TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']
const BLOCKING = new Set(['serious', 'critical'])

/**
 * Corre o axe na página atual e devolve só as violações que bloqueiam.
 * Exclui as conquistas por desbloquear (`.ach-card.locked`): estão esbatidas de
 * propósito (opacity) como estado inativo, que o critério WCAG 1.4.3 dispensa de
 * contraste — mas o axe não sabe distinguir "inativo" de "texto real".
 */
async function scan(page, testInfo, label) {
  const results = await new AxeBuilder({ page }).withTags(TAGS).exclude('.ach-card.locked').analyze()
  await testInfo.attach(`axe-${label}`, {
    body: JSON.stringify(results.violations, null, 2),
    contentType: 'application/json',
  })
  return results.violations.filter((v) => BLOCKING.has(v.impact))
}

/** Mensagem legível quando há violações (id · impacto · nós afetados). */
const fmt = (violations) =>
  violations.map((v) => `${v.id} [${v.impact}] ×${v.nodes.length}: ${v.help}`).join('\n')

for (const colorScheme of /** @type {const} */ (['light', 'dark'])) {
  test.describe(`acessibilidade (axe-core) — tema ${colorScheme}`, () => {
    test.use({ colorScheme })

    test('página de autenticação', async ({ page }, testInfo) => {
      await page.goto('/')
      await expect(page.getByRole('button', { name: 'Entrar' })).toBeVisible()
      const v = await scan(page, testInfo, `auth-${colorScheme}`)
      expect(v, fmt(v)).toEqual([])
    })

    for (const tab of ['Painel', 'Rendimento', 'Despesas', 'Investimentos', 'Objetivos', 'Calendário']) {
      test(`separador ${tab}`, async ({ page }, testInfo) => {
        await registerViaUi(page)
        await sidebarTab(page, tab).click()
        await expect(sidebarTab(page, tab)).toHaveClass(/active/)
        // espera o conteúdo real (as páginas mostram um skeleton enquanto carregam)
        await expect(page.locator('.skeleton')).toHaveCount(0)
        const v = await scan(page, testInfo, `${tab.toLowerCase()}-${colorScheme}`)
        expect(v, fmt(v)).toEqual([])
      })
    }

    test('conquistas', async ({ page }, testInfo) => {
      await registerViaUi(page)
      await profileMenuAction(page, 'Conquistas')
      await expect(page.getByRole('heading', { name: /Nível 1/ })).toBeVisible()
      const v = await scan(page, testInfo, `conquistas-${colorScheme}`)
      expect(v, fmt(v)).toEqual([])
    })
  })
}

import { expect } from '@playwright/test'

/** Email único por execução — os testes nunca tocam em contas existentes. */
export function uniqueEmail() {
  return `e2e-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@test.pt`
}

export const PASSWORD = 'segredo123'

/**
 * Separador da sidebar (desktop). O mesmo label existe também na bottom-nav
 * mobile (escondida por CSS), por isso o locator é scoped à sidebar.
 */
export function sidebarTab(page, label) {
  return page.locator('.sidebar').getByRole('button', { name: label })
}

/**
 * Conquistas e Terminar sessão vivem no menu de perfil (atrás do avatar),
 * não na navegação. Abre o menu e clica no item pedido.
 */
export async function profileMenuAction(page, label) {
  await page.getByRole('button', { name: 'Perfil e definições' }).click()
  await page.getByRole('menuitem', { name: label }).click()
}

/**
 * Cria uma conta nova pela UI e espera pelo dashboard.
 * Devolve os dados do utilizador criado.
 */
export async function registerViaUi(page, { name = 'Utilizador E2E', email = uniqueEmail() } = {}) {
  await page.goto('/')
  await page.getByRole('button', { name: 'Criar conta' }).first().click()
  await page.getByPlaceholder('O teu nome').fill(name)
  await page.getByPlaceholder('exemplo@email.com').fill(email)
  await page.getByPlaceholder('Mínimo 6 caracteres').fill(PASSWORD)
  // código de convite fica vazio (registo aberto em dev)
  await page.getByRole('button', { name: 'Criar conta' }).last().click()
  await expect(sidebarTab(page, 'Painel')).toBeVisible()
  return { name, email }
}

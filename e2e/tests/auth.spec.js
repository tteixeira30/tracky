import { expect, test } from '@playwright/test'
import { PASSWORD, registerViaUi, sidebarTab, uniqueEmail } from './helpers.js'

test.describe('autenticação', () => {
  test('registo → dashboard → logout → login de novo', async ({ page }) => {
    const { email } = await registerViaUi(page, { name: 'Maria Teste', email: uniqueEmail() })

    // sessão iniciada: brand e separadores visíveis
    await expect(page.locator('.sidebar .brand')).toContainText('Tracky')
    await expect(sidebarTab(page, 'Rendimento')).toBeVisible()
    await expect(page.locator('.user-chip')).toContainText('Maria Teste')

    // terminar sessão
    await page.getByRole('button', { name: 'Terminar sessão' }).click()
    await expect(page.getByRole('button', { name: 'Entrar' })).toBeVisible()

    // entrar de novo com as mesmas credenciais
    await page.getByPlaceholder('exemplo@email.com').fill(email)
    await page.getByPlaceholder('A tua palavra-passe').fill(PASSWORD)
    await page.getByRole('button', { name: 'Entrar' }).click()
    await expect(sidebarTab(page, 'Painel')).toBeVisible()
  })

  test('password errada mostra erro e não inicia sessão', async ({ page }) => {
    const { email } = await registerViaUi(page)
    await page.getByRole('button', { name: 'Terminar sessão' }).click()
    await expect(page.getByRole('button', { name: 'Entrar' })).toBeVisible()

    await page.getByPlaceholder('exemplo@email.com').fill(email)
    await page.getByPlaceholder('A tua palavra-passe').fill('errada999')
    await page.getByRole('button', { name: 'Entrar' }).click()

    await expect(page.getByText('Erro ao iniciar sessão')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Entrar' })).toBeVisible()
  })

  test('submeter sem preencher mostra "Campos em falta"', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('button', { name: 'Entrar' }).click()
    await expect(page.getByText('Campos em falta')).toBeVisible()
  })
})

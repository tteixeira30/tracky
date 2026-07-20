import { expect, test } from '@playwright/test'
import { registerViaUi, sidebarTab } from './helpers.js'

/**
 * Despesas — contas correntes, movimentos manuais e importação de extratos.
 * Tudo determinístico: os movimentos são criados/importados pelo teste (sem
 * dependência de cotações externas). As datas do extrato usam o mês atual, que é
 * o mês por omissão da página (para os movimentos aparecerem sem navegar).
 */

/** Mês atual no formato AAAA-MM — igual ao default da ExpensesPage. */
function currentYm() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

/** A secção de contas (incluindo o chip "+ Conta") está sempre visível na página. */
async function criarConta(page, { name, saldo } = {}) {
  await page.locator('.account-chip.add').click()
  const dialog = page.getByRole('dialog')
  await dialog.getByPlaceholder('Ex: Santander').fill(name)
  if (saldo != null) await dialog.getByPlaceholder(/Deixa em branco/).fill(String(saldo))
  await dialog.getByRole('button', { name: 'Guardar' }).click()
  await expect(dialog).toBeHidden()
  await expect(page.locator('.account-chip', { hasText: name })).toBeVisible()
}

async function criarMovimento(page, { descricao, valor, tipo = 'Saída', categoria } = {}) {
  await page.getByRole('button', { name: 'Novo movimento' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByPlaceholder('Ex: Supermercado Continente').fill(descricao)
  if (tipo === 'Entrada') await dialog.getByRole('button', { name: 'Entrada' }).click()
  if (categoria) {
    // dropdowns do modal: [0] Conta, [1] Categoria
    await dialog.locator('.dd-trigger').nth(1).click()
    await page.getByRole('option', { name: categoria, exact: true }).click()
  }
  await dialog.getByPlaceholder('0', { exact: true }).fill(String(valor))
  await dialog.getByRole('button', { name: 'Guardar' }).click()
  await expect(dialog).toBeHidden()
}

/** Botão "Importar extrato" do cabeçalho (o estado vazio também tem um igual). */
function importarBtn(page) {
  return page.locator('.page-actions').getByRole('button', { name: 'Importar extrato' })
}

/** Vai para a página de Despesas depois do registo. */
async function irParaDespesas(page) {
  await registerViaUi(page)
  await sidebarTab(page, 'Despesas').click()
  await expect(page.getByRole('heading', { name: 'Despesas', exact: true })).toBeVisible()
}

test.describe('despesas — contas', () => {
  test('sem contas mostra estado vazio e as ações estão bloqueadas', async ({ page }) => {
    await irParaDespesas(page)

    await expect(page.getByText('Começa por criar as tuas contas')).toBeVisible()
    // sem contas não se pode adicionar/importar movimentos
    await expect(page.getByRole('button', { name: 'Novo movimento' })).toBeDisabled()
    await expect(importarBtn(page)).toBeDisabled()
  })

  test('criar a primeira conta desbloqueia os movimentos', async ({ page }) => {
    await irParaDespesas(page)
    await criarConta(page, { name: 'Conta Corrente E2E' })

    await expect(page.getByRole('button', { name: 'Novo movimento' })).toBeEnabled()
    await expect(importarBtn(page)).toBeEnabled()
    // já não é o estado "cria contas", passa a "sem movimentos"
    await expect(page.getByText(/Sem movimentos em/)).toBeVisible()
  })

  test('conta com saldo alimenta o KPI "Saldo em contas"', async ({ page }) => {
    await irParaDespesas(page)
    await criarConta(page, { name: 'Poupança E2E', saldo: '1000' })
    await criarConta(page, { name: 'Ordenado E2E', saldo: '500' })

    // soma das duas contas com saldo definido
    const saldoKpi = page.locator('.kpi-card', { hasText: 'Saldo em contas' })
    await expect(saldoKpi.locator('.kpi-value')).toHaveText(/1500,00\s*€/)
    await expect(saldoKpi.locator('.kpi-sub')).toContainText('2 de 2 conta(s) com saldo')
  })

  test('filtrar por conta mostra o saldo dessa conta', async ({ page }) => {
    await irParaDespesas(page)
    await criarConta(page, { name: 'Conta A E2E', saldo: '1000' })
    await criarConta(page, { name: 'Conta B E2E', saldo: '250' })

    await page.locator('.account-chip', { hasText: 'Conta B E2E' }).click()

    const saldoKpi = page.locator('.kpi-card', { hasText: 'Saldo da conta' })
    await expect(saldoKpi.locator('.kpi-value')).toHaveText(/250,00\s*€/)
  })

  test('eliminar uma conta remove-a e aos seus movimentos', async ({ page }) => {
    await irParaDespesas(page)
    await criarConta(page, { name: 'Descartável E2E' })
    await criarMovimento(page, { descricao: 'Movimento na conta', valor: '10' })
    await expect(page.locator('.event-row', { hasText: 'Movimento na conta' })).toBeVisible()

    const chip = page.locator('.account-chip', { hasText: 'Descartável E2E' })
    await chip.hover()
    await chip.getByRole('button', { name: 'Eliminar Descartável E2E' }).click()
    await expect(page.getByText('Eliminar conta?')).toBeVisible()
    await page.locator('.btn.danger', { hasText: 'Eliminar' }).click()

    await expect(page.locator('.account-chip', { hasText: 'Descartável E2E' })).toHaveCount(0)
    // volta ao estado vazio inicial
    await expect(page.getByText('Começa por criar as tuas contas')).toBeVisible()
  })
})

test.describe('despesas — movimentos', () => {
  test('adicionar uma despesa atualiza saídas, saldo e categorias', async ({ page }) => {
    await irParaDespesas(page)
    await criarConta(page, { name: 'Conta E2E' })
    await criarMovimento(page, { descricao: 'Continente E2E', valor: '45.30', categoria: 'Supermercado' })

    // aparece na lista de movimentos
    await expect(page.locator('.event-row', { hasText: 'Continente E2E' })).toBeVisible()

    // KPIs: saídas = 45,30 · saldo do mês negativo
    await expect(page.locator('.kpi-card', { hasText: 'Saídas' }).locator('.kpi-value')).toHaveText(/45,30\s*€/)
    const saldoMes = page.locator('.kpi-card', { hasText: 'Saldo do mês' }).locator('.kpi-value')
    await expect(saldoMes).toHaveText(/45,30\s*€/)
    await expect(saldoMes).toHaveClass(/neg/)

    // gráfico de despesas por categoria inclui "Supermercado"
    await expect(page.locator('.cat-bars', { hasText: 'Supermercado' })).toBeVisible()
  })

  test('uma entrada conta como entrada e deixa o saldo positivo', async ({ page }) => {
    await irParaDespesas(page)
    await criarConta(page, { name: 'Conta E2E' })
    await criarMovimento(page, { descricao: 'Salário E2E', valor: '1500', tipo: 'Entrada' })

    await expect(page.locator('.kpi-card', { hasText: 'Entradas' }).locator('.kpi-value')).toHaveText(/1500,00\s*€/)
    const saldoMes = page.locator('.kpi-card', { hasText: 'Saldo do mês' }).locator('.kpi-value')
    await expect(saldoMes).toHaveText(/1500,00\s*€/)
    await expect(saldoMes).toHaveClass(/pos/)
  })

  test('editar a descrição de um movimento', async ({ page }) => {
    await irParaDespesas(page)
    await criarConta(page, { name: 'Conta E2E' })
    await criarMovimento(page, { descricao: 'Descrição Antiga', valor: '20' })

    const row = page.locator('.event-row', { hasText: 'Descrição Antiga' })
    await row.getByRole('button', { name: 'Editar' }).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByPlaceholder('Ex: Supermercado Continente').fill('Descrição Nova')
    await dialog.getByRole('button', { name: 'Guardar' }).click()
    await expect(dialog).toBeHidden()

    await expect(page.locator('.event-row', { hasText: 'Descrição Nova' })).toBeVisible()
    await expect(page.locator('.event-row', { hasText: 'Descrição Antiga' })).toHaveCount(0)
  })

  test('eliminar um movimento remove-o da lista', async ({ page }) => {
    await irParaDespesas(page)
    await criarConta(page, { name: 'Conta E2E' })
    await criarMovimento(page, { descricao: 'A Eliminar', valor: '5' })

    const row = page.locator('.event-row', { hasText: 'A Eliminar' })
    await row.getByRole('button', { name: 'Eliminar' }).click()
    await expect(page.getByText('Eliminar movimento?')).toBeVisible()
    await page.locator('.btn.danger', { hasText: 'Eliminar' }).click()

    await expect(page.locator('.event-row', { hasText: 'A Eliminar' })).toHaveCount(0)
  })
})

test.describe('despesas — importação de extrato', () => {
  test('importar um CSV genérico cria os movimentos e ignora duplicados na 2.ª vez', async ({ page }) => {
    await irParaDespesas(page)
    await criarConta(page, { name: 'Conta Extrato E2E' })

    const ym = currentYm()
    const csv = [
      'Data,Descrição,Montante',
      `${ym}-05,Continente Lisboa,-45.30`,
      `${ym}-06,Salário Julho,1500.00`,
      `${ym}-07,Netflix,-12.99`,
    ].join('\n')

    // ---- 1.ª importação: 3 movimentos ----
    await importarBtn(page).click()
    let dialog = page.getByRole('dialog')
    await dialog.locator('input[type="file"]').setInputFiles({
      name: 'extrato.csv', mimeType: 'text/csv', buffer: Buffer.from(csv),
    })
    // pré-visualização: formato genérico reconhecido, 3 movimentos prontos
    await expect(dialog.getByText(/3 movimento\(s\) prontos a importar/)).toBeVisible()
    await dialog.getByRole('button', { name: /Importar 3 movimento/ }).click()
    await expect(dialog).toBeHidden()

    await expect(page.getByText('Continente Lisboa')).toBeVisible()
    await expect(page.getByText('Salário Julho')).toBeVisible()
    await expect(page.getByText('Netflix')).toBeVisible()
    // categorização automática: Continente → Supermercado
    await expect(page.locator('.cat-bars', { hasText: 'Supermercado' })).toBeVisible()

    // ---- 2.ª importação do mesmo ficheiro: tudo ignorado (dedupe) ----
    await importarBtn(page).click()
    dialog = page.getByRole('dialog')
    await dialog.locator('input[type="file"]').setInputFiles({
      name: 'extrato.csv', mimeType: 'text/csv', buffer: Buffer.from(csv),
    })
    await expect(dialog.getByText(/3 movimento\(s\) prontos a importar/)).toBeVisible()
    await dialog.getByRole('button', { name: /Importar 3 movimento/ }).click()
    await expect(dialog).toBeHidden()

    // nada duplicou — continua a haver exatamente uma linha "Continente Lisboa"
    await expect(page.getByText('Continente Lisboa')).toHaveCount(1)
  })
})

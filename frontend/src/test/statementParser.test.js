import { describe, expect, it } from 'vitest'
import { parseAmount, parseDate, buildTransactions } from '../statementParser'

describe('parseAmount — formatos monetários', () => {
  it('formato PT (1.234,56)', () => {
    expect(parseAmount('1.234,56')).toBe(1234.56)
  })
  it('formato EN (1,234.56)', () => {
    expect(parseAmount('1,234.56')).toBe(1234.56)
  })
  it('negativo com sinal', () => {
    expect(parseAmount('-45,90')).toBe(-45.9)
  })
  it('negativo entre parênteses (contabilístico)', () => {
    expect(parseAmount('(12,34)')).toBe(-12.34)
  })
  it('com símbolo de moeda e espaços', () => {
    expect(parseAmount(' 1 234,50 €')).toBe(1234.5)
  })
  it('vazio ou inválido devolve null', () => {
    expect(parseAmount('')).toBeNull()
    expect(parseAmount('abc')).toBeNull()
    expect(parseAmount(null)).toBeNull()
  })
})

describe('parseDate — datas com e sem ano', () => {
  it('ISO', () => expect(parseDate('2026-03-15')).toBe('2026-03-15'))
  it('DD/MM/AAAA (PT)', () => expect(parseDate('15/03/2026')).toBe('2026-03-15'))
  it('DD-MM sem ano usa a data de referência do documento', () => {
    expect(parseDate('05-03', '2026-03-31')).toBe('2026-03-05')
  })
  it('DD-MM muito depois da referência recua um ano (extrato de janeiro com movimentos de dezembro)', () => {
    expect(parseDate('28-12', '2026-01-10')).toBe('2025-12-28')
  })
})

describe('buildTransactions — sinal e valor dos movimentos', () => {
  it('coluna montante com sinais: separa entradas e saídas e guarda o valor absoluto', () => {
    const mapping = { date: 0, description: 1, amount: 2, debit: -1, credit: -1, currency: -1, state: -1, fee: -1, balance: -1 }
    const { rows, ignored } = buildTransactions([
      ['2026-03-01', 'Salário', '2000,00'],
      ['2026-03-05', 'Continente', '-85,40'],
    ], mapping, '2026-03-31')

    expect(ignored).toBe(0)
    expect(rows).toHaveLength(2)
    expect(rows[0]).toMatchObject({ description: 'Salário', amount: 2000, inflow: true })
    expect(rows[1]).toMatchObject({ description: 'Continente', amount: 85.4, inflow: false })
  })

  it('colunas débito/crédito: débito é saída, crédito é entrada', () => {
    const mapping = { date: 0, description: 1, amount: -1, debit: 2, credit: 3, currency: -1, state: -1, fee: -1, balance: -1 }
    const { rows } = buildTransactions([
      ['2026-03-01', 'Ordenado', '', '1500,00'],
      ['2026-03-08', 'Renda', '700,00', ''],
    ], mapping, '2026-03-31')

    expect(rows[0]).toMatchObject({ amount: 1500, inflow: true })
    expect(rows[1]).toMatchObject({ amount: 700, inflow: false })
  })

  it('ignora movimentos em moeda diferente de EUR', () => {
    const mapping = { date: 0, description: 1, amount: 2, debit: -1, credit: -1, currency: 3, state: -1, fee: -1, balance: -1 }
    const { rows, ignored } = buildTransactions([
      ['2026-03-01', 'Compra EUR', '-10,00', 'EUR'],
      ['2026-03-02', 'Compra USD', '-10,00', 'USD'],
    ], mapping, '2026-03-31')

    expect(rows).toHaveLength(1)
    expect(rows[0].description).toBe('Compra EUR')
    expect(ignored).toBe(1)
  })

  it('ignora movimentos pendentes/revertidos pela coluna de estado', () => {
    const mapping = { date: 0, description: 1, amount: 2, debit: -1, credit: -1, currency: -1, state: 3, fee: -1, balance: -1 }
    const { rows, ignored } = buildTransactions([
      ['2026-03-01', 'Compra ok', '-10,00', 'COMPLETED'],
      ['2026-03-02', 'Compra pendente', '-10,00', 'PENDING'],
    ], mapping, '2026-03-31')

    expect(rows).toHaveLength(1)
    expect(rows[0].description).toBe('Compra ok')
    expect(ignored).toBe(1)
  })

  it('extrato sem sinais (PDF): infere entrada/saída pela evolução do saldo', () => {
    // saldos: 1000 → 914,60 (−85,40 Continente) → 899,60 (−15 Farmácia) → 2899,60 (+2000 Salário)
    const mapping = { date: 0, description: 1, amount: 2, debit: -1, credit: -1, currency: -1, state: -1, fee: -1, balance: 3 }
    const { rows } = buildTransactions([
      ['2026-03-05', 'Continente', '85,40', '914,60'],
      ['2026-03-15', 'Farmácia', '15,00', '899,60'],
      ['2026-03-25', 'Salário', '2000,00', '2899,60'],
    ], mapping, '2026-03-31')

    // movimentos deriváveis a partir da diferença entre saldos consecutivos
    expect(rows.find((r) => r.description === 'Farmácia').inflow).toBe(false)
    expect(rows.find((r) => r.description === 'Salário').inflow).toBe(true)
  })

  // BUG conhecido: num extrato PDF sem sinais, o PRIMEIRO movimento (o mais antigo) não
  // tem saldo anterior com que comparar, por isso o seu sentido não é determinável e fica
  // por omissão como ENTRADA — mesmo quando é uma saída. Este teste fixa o comportamento
  // atual para o tornar visível; afeta 1 movimento por importação (só PDFs sem sinais).
  it('[limitação] o primeiro movimento de um extrato sem sinais fica como entrada', () => {
    const mapping = { date: 0, description: 1, amount: 2, debit: -1, credit: -1, currency: -1, state: -1, fee: -1, balance: 3 }
    const { rows } = buildTransactions([
      ['2026-03-05', 'Continente', '85,40', '914,60'],
      ['2026-03-15', 'Farmácia', '15,00', '899,60'],
      ['2026-03-25', 'Salário', '2000,00', '2899,60'],
    ], mapping, '2026-03-31')

    // na realidade foi uma saída, mas o algoritmo não o consegue saber sem o saldo inicial
    expect(rows.find((r) => r.description === 'Continente').inflow).toBe(true)
  })

  it('subtrai a comissão (fee) ao valor do movimento', () => {
    const mapping = { date: 0, description: 1, amount: 2, debit: -1, credit: -1, currency: -1, state: -1, fee: 3, balance: -1 }
    const { rows } = buildTransactions([
      ['2026-03-01', 'Levantamento', '-100,00', '2,00'],
    ], mapping, '2026-03-31')

    // saída de 100 + 2 de comissão = 102 a sair da conta
    expect(rows[0]).toMatchObject({ amount: 102, inflow: false })
  })
})

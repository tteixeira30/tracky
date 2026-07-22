import { afterEach, describe, expect, it } from 'vitest'
import {
  DEFAULT_CATEGORIES, catLabel, catColor, categoryOptions, setCustomCategories,
} from '../categories'

// o registo de categorias personalizadas é estado partilhado do módulo — limpar entre testes
afterEach(() => setCustomCategories([]))

describe('categories', () => {
  it('resolve os rótulos e cores das categorias por omissão', () => {
    expect(catLabel('GROCERIES')).toBe('Supermercado')
    expect(catColor('GROCERIES')).toBe('#f59e0b')
    expect(catLabel('INCOME')).toBe('Rendimento')
  })

  it('cai em "Outros" para chaves desconhecidas', () => {
    expect(catLabel('CHAVE_QUE_NAO_EXISTE')).toBe('Outros')
    expect(catColor('CHAVE_QUE_NAO_EXISTE')).toBe(catColor('OTHER'))
  })

  it('regista categorias personalizadas e usa-as em catLabel/catColor', () => {
    setCustomCategories([{ key: 'EDUCACAO', label: 'Educação', color: '#22d3ee' }])
    expect(catLabel('EDUCACAO')).toBe('Educação')
    expect(catColor('EDUCACAO')).toBe('#22d3ee')
  })

  it('setCustomCategories substitui o registo anterior (não acumula)', () => {
    setCustomCategories([{ key: 'EDUCACAO', label: 'Educação', color: '#22d3ee' }])
    setCustomCategories([{ key: 'ANIMAIS', label: 'Animais', color: '#4ade80' }])
    expect(catLabel('ANIMAIS')).toBe('Animais')
    // a anterior deixou de estar registada → volta a "Outros"
    expect(catLabel('EDUCACAO')).toBe('Outros')
  })

  it('categoryOptions inclui as por omissão seguidas das personalizadas', () => {
    setCustomCategories([{ key: 'EDUCACAO', label: 'Educação', color: '#22d3ee' }])
    const opts = categoryOptions()
    // todas as por omissão presentes
    for (const key of DEFAULT_CATEGORIES) {
      expect(opts.find((o) => o.value === key)).toBeTruthy()
    }
    // a personalizada surge no fim
    const last = opts[opts.length - 1]
    expect(last).toEqual({ value: 'EDUCACAO', label: 'Educação' })
    expect(opts).toHaveLength(DEFAULT_CATEGORIES.length + 1)
  })
})

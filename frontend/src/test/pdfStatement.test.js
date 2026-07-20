import { describe, expect, it } from 'vitest'
import { mapTextItems, itemsToLines, linesToTable } from '../pdfStatement'

/**
 * Testa a reconstrução da tabela de um extrato PDF a partir das coordenadas dos
 * itens de texto (as funções puras de pdfStatement.js — o extractPdfRows depende
 * do pdf.js/worker e não é testável fora do browser).
 */

/** Item de texto no formato do pdf.js: transform = [escalaX, inclY, inclX, escalaY, x, y]. */
const pdfItem = (str, x, y, width, skewY = 0) => ({ str, width, transform: [10, skewY, 0, 10, x, y] })
/** Item já mapeado {str, x, y, width} para alimentar itemsToLines diretamente. */
const item = (str, x, y, width) => ({ str, x, y, width })
/** Célula {text, x, end} para alimentar linesToTable diretamente. */
const cell = (text, x, end) => ({ text, x, end })

describe('mapTextItems — normaliza itens do pdf.js', () => {
  it('extrai str/x/y/width do transform', () => {
    const [m] = mapTextItems([pdfItem('Continente', 100, 700, 48)])
    expect(m).toEqual({ str: 'Continente', x: 100, y: 700, width: 48 })
  })

  it('descarta texto rodado (rodapés verticais nas margens)', () => {
    const items = [
      pdfItem('Movimento', 100, 700, 48),
      pdfItem('rodapé vertical', 20, 400, 60, 9.9), // transform[1] ≠ 0 → rodado
    ]
    const mapped = mapTextItems(items)
    expect(mapped).toHaveLength(1)
    expect(mapped[0].str).toBe('Movimento')
  })
})

describe('itemsToLines — agrupa itens em linhas e células', () => {
  it('agrupa itens com Y próximo na mesma linha e separa colunas afastadas', () => {
    const lines = itemsToLines([
      item('Data', 50, 700, 20),
      item('Descrição', 120, 701, 40), // baseline ligeiramente diferente, mesma linha
      item('Valor', 300, 700, 20),
    ])
    expect(lines).toHaveLength(1)
    expect(lines[0].map((c) => c.text)).toEqual(['Data', 'Descrição', 'Valor'])
  })

  it('junta itens próximos na mesma célula, com espaço quando há folga', () => {
    // "Pingo"(50–70) + "Doce"(74–92): gap 4 ≤ 7 e > 1 → "Pingo Doce"
    const [line] = itemsToLines([
      item('Pingo', 50, 700, 20),
      item('Doce', 74, 700, 18),
    ])
    expect(line).toHaveLength(1)
    expect(line[0].text).toBe('Pingo Doce')
  })

  it('junta itens colados sem espaço (gap ≤ 1)', () => {
    // "12"(50–56) + ",34"(56.5–62.5): gap 0.5 → "12,34"
    const [line] = itemsToLines([
      item('12', 50, 700, 6),
      item(',34', 56.5, 700, 6),
    ])
    expect(line[0].text).toBe('12,34')
  })

  it('separa itens com folga grande em células distintas', () => {
    // gap 20 > 7 → duas células
    const [line] = itemsToLines([
      item('A', 50, 700, 6),
      item('B', 76, 700, 6),
    ])
    expect(line.map((c) => c.text)).toEqual(['A', 'B'])
  })

  it('ordena as linhas de cima para baixo (Y decrescente)', () => {
    const lines = itemsToLines([
      item('baixo', 50, 680, 20),
      item('cima', 50, 700, 20),
    ])
    expect(lines.map((l) => l[0].text)).toEqual(['cima', 'baixo'])
  })

  it('ignora strings vazias ou só com espaços', () => {
    const [line] = itemsToLines([
      item('Real', 50, 700, 20),
      item('   ', 120, 700, 20),
      item('', 300, 700, 20),
    ])
    expect(line.map((c) => c.text)).toEqual(['Real'])
  })
})

describe('linesToTable — alinha células às colunas do cabeçalho por posição X', () => {
  // Cabeçalho reconhecível: Data / Descrição / Débito / Crédito
  const header = [cell('Data', 50, 90), cell('Descrição', 120, 200), cell('Débito', 300, 340), cell('Crédito', 380, 420)]

  it('preenche a coluna certa mesmo quando faltam células na linha', () => {
    // linha sem débito: o crédito (x≈392) tem de cair na 4ª coluna, deixando a 3ª vazia
    const dataRow = [cell('05-03', 50, 78), cell('Pingo Doce', 120, 190), cell('45,90', 392, 418)]
    const table = linesToTable([header, dataRow])

    expect(table[0]).toEqual(['Data', 'Descrição', 'Débito', 'Crédito'])
    expect(table[1]).toEqual(['05-03', 'Pingo Doce', '', '45,90'])
  })

  it('ignora texto à esquerda da tabela (rodapé vertical / símbolo solto)', () => {
    // célula muito à esquerda da 1ª coluna (centro < leftEdge − 15) é descartada
    const dataRow = [cell('¤', 5, 15), cell('05-03', 50, 78), cell('Compras', 120, 190), cell('10,00', 312, 338)]
    const table = linesToTable([header, dataRow])
    expect(table[1]).toEqual(['05-03', 'Compras', '10,00', ''])
  })

  it('sem cabeçalho reconhecível devolve a matriz de texto crua', () => {
    const l1 = [cell('foo', 50, 70), cell('bar', 120, 140)]
    const l2 = [cell('baz', 50, 70), cell('qux', 120, 140)]
    const table = linesToTable([l1, l2])
    expect(table).toEqual([['foo', 'bar'], ['baz', 'qux']])
  })
})

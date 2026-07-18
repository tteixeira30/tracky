// Extração de movimentos de extratos bancários em PDF (pdf.js / pdfjs-dist).
// Reconstrói a tabela a partir das coordenadas dos itens de texto: agrupa itens
// por linha (Y), junta itens próximos na mesma célula (X) e alinha as células
// das linhas de dados às colunas do cabeçalho. Só funciona com PDFs com texto
// embebido — extratos digitalizados (imagem) precisariam de OCR.

import { HEADER_HINTS } from './statementParser.js'

/**
 * Agrupa itens de texto {str, x, y, width} numa lista de linhas, cada uma com
 * células {text, x, end}. Função pura (testável fora do browser).
 */
export function itemsToLines(items) {
  const clean = items.filter((it) => it.str && it.str.trim() !== '')
  clean.sort((a, b) => b.y - a.y || a.x - b.x)

  // agrupar por Y com tolerância (as baselines variam ligeiramente dentro da mesma linha)
  const lines = []
  for (const it of clean) {
    const line = lines.find((l) => Math.abs(l.y - it.y) <= 2.5)
    if (line) line.items.push(it)
    else lines.push({ y: it.y, items: [it] })
  }

  return lines.map((line) => {
    line.items.sort((a, b) => a.x - b.x)
    const cells = []
    let cur = null
    for (const it of line.items) {
      const gap = cur ? it.x - cur.end : Infinity
      if (cur && gap <= 7) {
        cur.text += (gap > 1 ? ' ' : '') + it.str
        cur.end = Math.max(cur.end, it.x + (it.width || 0))
      } else {
        cur = { text: it.str, x: it.x, end: it.x + (it.width || 0) }
        cells.push(cur)
      }
    }
    return cells.map((c) => ({ text: c.text.trim(), x: c.x, end: c.end }))
  })
}

/**
 * Converte linhas de células {text, x, end} em linhas de strings alinhadas às
 * colunas do cabeçalho da tabela (numa tabela PDF as células vazias não existem,
 * pelo que o alinhamento por índice falharia — usa-se a posição X).
 */
export function linesToTable(lines) {
  let headerIdx = -1
  for (let i = 0; i < lines.length; i++) {
    const hits = lines[i].filter((c) => HEADER_HINTS.test(c.text)).length
    if (hits >= 2 && lines[i].length >= 3) { headerIdx = i; break }
  }
  if (headerIdx === -1) return lines.map((l) => l.map((c) => c.text))

  const cols = lines[headerIdx].map((c) => ({ x: c.x, end: c.end, center: (c.x + c.end) / 2 }))
  const nearestCol = (cell) => {
    const center = (cell.x + cell.end) / 2
    let best = 0, bestDist = Infinity
    for (let k = 0; k < cols.length; k++) {
      // números costumam estar alinhados à direita e o cabeçalho à esquerda —
      // usa a menor das distâncias entre inícios, fins e centros
      const d = Math.min(
        Math.abs(center - cols[k].center),
        Math.abs(cell.x - cols[k].x),
        Math.abs(cell.end - cols[k].end),
      )
      if (d < bestDist) { bestDist = d; best = k }
    }
    return best
  }

  return lines.map((line, i) => {
    if (i === headerIdx) return cols.map((_, k) => lines[headerIdx][k].text)
    const row = new Array(cols.length).fill('')
    for (const cell of line) {
      const k = nearestCol(cell)
      row[k] = row[k] ? `${row[k]} ${cell.text}` : cell.text
    }
    return row
  })
}

/**
 * Lê um PDF (ArrayBuffer) e devolve { rows, hasText } com as linhas de células
 * de todas as páginas, prontas para analyzeRows(). A biblioteca é carregada por
 * dynamic import para não pesar no bundle principal.
 */
export async function extractPdfRows(data) {
  const pdfjs = await import('pdfjs-dist')
  const worker = await import('pdfjs-dist/build/pdf.worker.min.mjs?url')
  pdfjs.GlobalWorkerOptions.workerSrc = worker.default

  const task = pdfjs.getDocument({ data })
  const lines = []
  let hasText = false
  try {
    const doc = await task.promise
    for (let p = 1; p <= doc.numPages; p++) {
      const page = await doc.getPage(p)
      const content = await page.getTextContent()
      const items = content.items.map((it) => ({
        str: it.str, x: it.transform[4], y: it.transform[5], width: it.width,
      }))
      if (items.some((it) => it.str.trim() !== '')) hasText = true
      lines.push(...itemsToLines(items))
    }
  } finally {
    await task.destroy()
  }
  return { rows: linesToTable(lines), hasText }
}

// Parser de extratos bancários (CSV) no cliente.
// Suporta deteção automática de formatos conhecidos (Revolut, Santander PT e
// exportações genéricas com colunas Data/Descrição/Montante ou Débito/Crédito)
// e mapeamento manual de colunas quando a deteção falha.
// Os valores do extrato são assumidos em EUR (linhas noutra moeda são ignoradas).

// ---------- CSV ----------

/** Divide o texto CSV em linhas de células, respeitando aspas. */
export function parseCsv(text) {
  const delimiter = detectDelimiter(text)
  const rows = []
  let row = []
  let cell = ''
  let inQuotes = false
  for (let i = 0; i < text.length; i++) {
    const ch = text[i]
    if (inQuotes) {
      if (ch === '"') {
        if (text[i + 1] === '"') { cell += '"'; i++ } else inQuotes = false
      } else cell += ch
    } else if (ch === '"') {
      inQuotes = true
    } else if (ch === delimiter) {
      row.push(cell); cell = ''
    } else if (ch === '\n' || ch === '\r') {
      if (ch === '\r' && text[i + 1] === '\n') i++
      row.push(cell); cell = ''
      if (row.some((c) => c.trim() !== '')) rows.push(row)
      row = []
    } else cell += ch
  }
  row.push(cell)
  if (row.some((c) => c.trim() !== '')) rows.push(row)
  return rows
}

function detectDelimiter(text) {
  const sample = text.slice(0, 4000)
  const counts = [';', ',', '\t'].map((d) => ({ d, n: (sample.match(new RegExp(`\\${d}`, 'g')) || []).length }))
  counts.sort((a, b) => b.n - a.n)
  return counts[0].n > 0 ? counts[0].d : ','
}

// ---------- Datas e valores ----------

/** Converte texto para data ISO (AAAA-MM-DD); devolve null se não reconhecer. */
export function parseDate(raw) {
  if (!raw) return null
  const s = String(raw).trim().slice(0, 10)
  let m = s.match(/^(\d{4})-(\d{2})-(\d{2})/)
  if (m) return valid(m[1], m[2], m[3])
  m = s.match(/^(\d{1,2})[/.-](\d{1,2})[/.-](\d{4})/)
  if (m) return valid(m[3], m[2], m[1]) // dia/mês/ano (formato PT)
  m = s.match(/^(\d{1,2})[/.-](\d{1,2})[/.-](\d{2})$/)
  if (m) return valid(`20${m[3]}`, m[2], m[1])
  return null
}

function valid(y, mo, d) {
  const year = Number(y), month = Number(mo), day = Number(d)
  if (month < 1 || month > 12 || day < 1 || day > 31 || year < 1990 || year > 2100) return null
  return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
}

/** Converte texto monetário ("1.234,56", "-1,234.56", "(12,34)") para número; null se inválido. */
export function parseAmount(raw) {
  if (raw == null) return null
  let s = String(raw).trim().replace(/[€$£\s ]/g, '')
  if (!s) return null
  let negative = false
  if (/^\(.*\)$/.test(s)) { negative = true; s = s.slice(1, -1) }
  if (s.startsWith('-')) { negative = true; s = s.slice(1) }
  if (s.startsWith('+')) s = s.slice(1)
  if (!/^[\d.,]+$/.test(s)) return null
  const lastComma = s.lastIndexOf(',')
  const lastDot = s.lastIndexOf('.')
  if (lastComma > lastDot) s = s.replace(/\./g, '').replace(',', '.')
  else s = s.replace(/,/g, '')
  const n = Number(s)
  if (!Number.isFinite(n)) return null
  return negative ? -n : n
}

// ---------- Categorização automática ----------

const CATEGORY_RULES = [
  ['GROCERIES', /continente|pingo doce|lidl|aldi|mercadona|auchan|intermarch|minipre[çc]o|supermercado|froiz|spar\b/i],
  ['RESTAURANT', /restaurante|mcdonald|burger|kfc|pizza|sushi|caf[eé]\b|pastelaria|padaria|uber\s*eats|glovo|bolt\s*food|telepizza|starbucks/i],
  ['TRANSPORT', /uber(?!\s*eats)|bolt(?!\s*food)|cp\s|metro|carris|galp|bp\b|repsol|cepsa|prio\b|combust|gasolina|via\s*verde|brisa|flixbus|ryanair|easyjet|tap\b/i],
  ['HOUSING', /renda|condom[ií]nio|edp\b|endesa|iberdrola|goldenergy|[aá]guas|meo\b|nos\b|vodafone|digi\b|luz\b|g[aá]s\b|eletricidade/i],
  ['SUBSCRIPTION', /netflix|spotify|hbo|disney|prime|youtube|icloud|apple\.com|google\s*(one|play)|playstation|xbox|crunchyroll|dazn|subscri/i],
  ['SHOPPING', /amazon|fnac|worten|zara|h&m|primark|decathlon|ikea|leroy|aliexpress|temu|shein|el\s*corte/i],
  ['HEALTH', /farm[aá]cia|hospital|cl[ií]nica|dentista|wells|cuf\b|lus[ií]adas|seguro\s*sa[uú]de|gin[aá]sio|fitness|solinca/i],
  ['LEISURE', /cinema|teatro|concerto|museu|bilhete|ticketline|steam|epic\s*games|nintendo|viagem|hotel|booking|airbnb/i],
  ['INCOME', /sal[aá]rio|vencimento|ordenado|payroll|reembolso|juros\s*(recebidos)?|dividendo/i],
  ['TRANSFER', /transfer[eê]ncia|trf\b|mb\s*way|mbway|levantamento|dep[oó]sito|top.?up|carregamento|revolut|trade\s*republic/i],
]

/** Sugere uma categoria a partir da descrição do movimento. */
export function autoCategory(description, inflow) {
  const d = description || ''
  for (const [cat, re] of CATEGORY_RULES) {
    if (re.test(d)) return cat
  }
  return inflow ? 'INCOME' : 'OTHER'
}

// ---------- Deteção de formato / mapeamento ----------

export const HEADER_HINTS = /data|date|descri|description|montante|amount|valor|d[eé]bito|debit|cr[eé]dito|credit|movimento|saldo|balance|currency|moeda/i

/**
 * Analisa um CSV de extrato: encontra a linha de cabeçalho, deteta o formato e
 * propõe um mapeamento de colunas. Devolve { format, headers, dataRows, mapping }.
 * mapping: índices { date, description, amount, debit, credit, currency, state, fee, balance }
 * (amount OU debit/credit; -1 = coluna inexistente).
 */
export function analyzeStatement(text) {
  return analyzeRows(parseCsv(text))
}

/** Variante de analyzeStatement para linhas já estruturadas (ex.: extraídas de um PDF). */
export function analyzeRows(rows) {
  if (!rows || rows.length === 0) return null

  let headerIdx = -1
  for (let i = 0; i < Math.min(rows.length, 12); i++) {
    const hits = rows[i].filter((c) => HEADER_HINTS.test(c)).length
    if (hits >= 2) { headerIdx = i; break }
  }
  if (headerIdx === -1) return { format: 'unknown', headers: rows[0], dataRows: rows.slice(1), mapping: emptyMapping() }

  const headers = rows[headerIdx].map((h) => h.trim())
  const dataRows = rows.slice(headerIdx + 1)
  const lower = headers.map((h) => h.toLowerCase())
  const find = (re) => lower.findIndex((h) => re.test(h))

  const mapping = {
    date: -1, description: -1, amount: -1, debit: -1, credit: -1,
    currency: find(/^currency$|^moeda$/), state: find(/^state$|^estado$/), fee: find(/^fee$|^comiss/),
    balance: find(/saldo|balance/),
  }

  let format = 'generic'
  if ((find(/completed date/) !== -1 && find(/started date/) !== -1)
      || (find(/data de conclus/) !== -1 && find(/data de in[ií]cio/) !== -1)) {
    // Revolut (EN): Type, Product, Started Date, Completed Date, Description, Amount, Fee, Currency, State, Balance
    // Revolut (PT): Tipo, Produto, Data de início, Data de Conclusão, Descrição, Montante, Comissão, Moeda, Estado, Saldo
    format = 'revolut'
    mapping.date = find(/completed date|data de conclus/)
    mapping.description = find(/^description$|^descri/)
    mapping.amount = find(/^amount$|^montante$/)
  } else {
    mapping.date = find(/data.*(opera|mov|lan[çc])/) !== -1 ? find(/data.*(opera|mov|lan[çc])/) : find(/^data\b|date/)
    mapping.description = find(/descri|description|movimento|detalhe|details?|narrative|memo|referência|referencia|concept/)
    mapping.debit = find(/d[eé]bito|debit(?!\s*card)/)
    mapping.credit = find(/cr[eé]dito|credit(?!\s*card)/)
    if (mapping.debit === -1 || mapping.credit === -1) {
      mapping.debit = -1; mapping.credit = -1
      mapping.amount = find(/montante|^amount$|^valor\b|import[aâ]ncia|^value/)
    }
    if (find(/santander/) !== -1 || rows.slice(0, 6).some((r) => r.some((c) => /santander/i.test(c)))) format = 'santander'
  }

  if (mapping.date === -1 || mapping.description === -1 || (mapping.amount === -1 && mapping.debit === -1)) {
    format = 'unknown'
  }
  return { format, headers, dataRows, mapping }
}

function emptyMapping() {
  return { date: -1, description: -1, amount: -1, debit: -1, credit: -1, currency: -1, state: -1, fee: -1, balance: -1 }
}

/**
 * Constrói as transações a importar a partir das linhas de dados e do mapeamento.
 * Devolve { rows: [{date, description, amount, inflow, category}], ignored }.
 */
export function buildTransactions(dataRows, mapping) {
  const out = []
  let ignored = 0
  for (const cells of dataRows) {
    const date = parseDate(cells[mapping.date])
    const description = (cells[mapping.description] || '').trim()
    if (!date || !description) { ignored++; continue }

    if (mapping.state !== -1) {
      // ignora movimentos não finalizados (pendentes, revertidos, recusados…) em qualquer idioma conhecido
      const state = (cells[mapping.state] || '').trim()
      if (state && /pend|revert|declin|fail|cancel|recus|anulad|estorn/i.test(state)) { ignored++; continue }
    }
    if (mapping.currency !== -1) {
      const cur = (cells[mapping.currency] || '').trim().toUpperCase()
      if (cur && cur !== 'EUR') { ignored++; continue }
    }

    let value = null
    if (mapping.amount !== -1) {
      value = parseAmount(cells[mapping.amount])
      if (value != null && mapping.fee !== -1) {
        const fee = parseAmount(cells[mapping.fee])
        if (fee) value -= Math.abs(fee)
      }
    } else {
      const debit = parseAmount(cells[mapping.debit])
      const credit = parseAmount(cells[mapping.credit])
      if (debit) value = -Math.abs(debit)
      else if (credit) value = Math.abs(credit)
    }
    if (value == null || value === 0) { ignored++; continue }

    out.push({
      date, description, value,
      balance: mapping.balance !== -1 ? parseAmount(cells[mapping.balance]) : null,
    })
  }

  // Extratos sem sinal no montante (comum em PDF): infere entrada/saída pela evolução do saldo.
  if (mapping.amount !== -1 && mapping.balance !== -1 && out.length >= 2 && !out.some((r) => r.value < 0)) {
    const signs = inferSignsFromBalance(out)
    if (signs) for (let i = 0; i < out.length; i++) {
      if (signs[i] !== 0) out[i].value = signs[i] * Math.abs(out[i].value)
    }
  }

  return {
    rows: out.map((r) => {
      const inflow = r.value > 0
      return {
        date: r.date, description: r.description,
        amount: Math.round(Math.abs(r.value) * 100) / 100,
        inflow,
        category: autoCategory(r.description, inflow),
      }
    }),
    ignored,
  }
}

/**
 * Dado [{value (abs), balance}], tenta deduzir o sinal de cada movimento comparando
 * saldos consecutivos, nas duas ordens possíveis (mais antigo primeiro / mais recente
 * primeiro). Devolve um array de -1/0/+1, ou null se os saldos não forem consistentes.
 */
function inferSignsFromBalance(entries) {
  const n = entries.length
  let best = null, bestScore = -1
  let comparable = 0
  for (const dir of [1, -1]) {
    const signs = new Array(n).fill(0)
    let score = 0
    let pairs = 0
    for (let i = 0; i < n - 1; i++) {
      // dir=1: ordem cronológica (saldo[i+1] = saldo[i] ± valor[i+1]); dir=-1: mais recente primeiro
      const [prev, next, j] = dir === 1 ? [entries[i], entries[i + 1], i + 1] : [entries[i + 1], entries[i], i]
      if (prev.balance == null || next.balance == null) continue
      pairs++
      const delta = next.balance - prev.balance
      const amt = Math.abs(entries[j].value)
      if (Math.abs(delta - amt) < 0.015) { signs[j] = 1; score++ }
      else if (Math.abs(delta + amt) < 0.015) { signs[j] = -1; score++ }
    }
    comparable = Math.max(comparable, pairs)
    if (score > bestScore) { bestScore = score; best = signs }
  }
  // só aceita se a grande maioria dos pares consecutivos bater certo
  if (comparable === 0 || bestScore < Math.max(1, Math.ceil(comparable * 0.6))) return null
  return best
}

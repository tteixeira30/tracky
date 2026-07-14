const BASE = '/api'
const TOKEN_KEY = 'tracky_token'

let onUnauthorized = null
export const setOnUnauthorized = (fn) => { onUnauthorized = fn }

export const getToken = () => localStorage.getItem(TOKEN_KEY)
export const setToken = (token) => localStorage.setItem(TOKEN_KEY, token)
export const clearToken = () => localStorage.removeItem(TOKEN_KEY)

async function request(path, options = {}) {
  const headers = { 'Content-Type': 'application/json' }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`

  const res = await fetch(BASE + path, { headers, ...options })

  if (res.status === 401 && !path.startsWith('/auth/')) {
    clearToken()
    onUnauthorized?.()
    throw new Error('Sessão expirada. Inicia sessão novamente.')
  }

  const text = await res.text()
  if (!res.ok) {
    let message = `Erro ${res.status}`
    try { message = JSON.parse(text).message || message } catch { if (text) message = text }
    throw new Error(message)
  }
  return text ? JSON.parse(text) : null
}

export const api = {
  // Painel geral
  getDashboard: () => request('/dashboard'),

  // Conquistas
  getAchievements: () => request('/achievements'),

  // Calendário financeiro
  getCalendar: (month) => request(`/calendar${month ? `?month=${month}` : ''}`),
  getUpcoming: (days = 60) => request(`/calendar/upcoming?days=${days}`),
  addCalendarEvent: (data) => request('/calendar/events', { method: 'POST', body: JSON.stringify(data) }),
  updateCalendarEvent: (id, data) => request(`/calendar/events/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteCalendarEvent: (id) => request(`/calendar/events/${id}`, { method: 'DELETE' }),
  setBalance: (balance) => request('/calendar/balance', { method: 'PUT', body: JSON.stringify({ balance }) }),

  // Moeda
  getCurrency: () => request('/currency'),
  setCurrency: (baseCurrency) => request('/auth/me/currency', { method: 'PUT', body: JSON.stringify({ baseCurrency }) }),

  // Autenticação
  register: (data) => request('/auth/register', { method: 'POST', body: JSON.stringify(data) }),
  login: (data) => request('/auth/login', { method: 'POST', body: JSON.stringify(data) }),
  me: () => request('/auth/me'),

  // Rendimento (mensal)
  getIncome: (month) => request(`/income${month ? `?month=${month}` : ''}`),
  setIncome: (monthlyIncome, month) => request(`/income${month ? `?month=${month}` : ''}`, { method: 'PUT', body: JSON.stringify({ monthlyIncome }) }),
  addAllocation: (data, month) => request(`/income/allocations${month ? `?month=${month}` : ''}`, { method: 'POST', body: JSON.stringify(data) }),
  updateAllocation: (id, data) => request(`/income/allocations/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteAllocation: (id) => request(`/income/allocations/${id}`, { method: 'DELETE' }),

  // Investimentos
  getInvestments: () => request('/investments'),
  addInvestment: (data) => request('/investments', { method: 'POST', body: JSON.stringify(data) }),
  updateInvestment: (id, data) => request(`/investments/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteInvestment: (id) => request(`/investments/${id}`, { method: 'DELETE' }),
  getPortfolioHistory: (range) => request(`/investments/portfolio/history?range=${range}`),
  getProjection: (months, monthly, type, customRate) => {
    let q = `months=${months}&monthly=${monthly || 0}`
    if (type && type !== 'all') q += `&type=${type}`
    if (customRate != null && customRate !== '') q += `&customRate=${customRate}`
    return request(`/investments/projection?${q}`)
  },

  // Depósitos mensais automáticos
  applyDeposits: (scope) => request(`/contributions/apply?scope=${scope}&force=true`, { method: 'POST' }),

  // Objetivos
  getGoals: () => request('/goals'),
  addGoal: (data) => request('/goals', { method: 'POST', body: JSON.stringify(data) }),
  updateGoal: (id, data) => request(`/goals/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  contributeGoal: (id, amount) => request(`/goals/${id}/contribute`, { method: 'POST', body: JSON.stringify({ amount }) }),
  deleteGoal: (id) => request(`/goals/${id}`, { method: 'DELETE' }),
}

// ---------- Moeda de apresentação ----------
// O backend devolve sempre valores em EUR; aqui convertem-se para a moeda base
// escolhida pelo utilizador (rate = quantas unidades da base valem 1 EUR).
let displayCurrency = 'EUR'
let displayRate = 1

export const setDisplayCurrency = (currency, rateFromEur) => {
  displayCurrency = currency || 'EUR'
  displayRate = Number(rateFromEur) > 0 ? Number(rateFromEur) : 1
}
export const getDisplayCurrency = () => displayCurrency

/** Converte um valor introduzido na moeda base para EUR (para enviar ao backend). */
export const toEur = (baseValue) => {
  const n = Number(baseValue)
  if (!Number.isFinite(n)) return baseValue
  return displayRate === 1 ? n : n / displayRate
}

/** Formata um valor em EUR, convertido e apresentado na moeda base. */
export const fmtEur = (v) =>
  v == null ? '—' : new Intl.NumberFormat('pt-PT', { style: 'currency', currency: displayCurrency }).format(v * displayRate)

/** Versão curta (sem casas decimais) para eixos de gráficos, na moeda base. */
export const fmtMoneyShort = (v) => {
  if (v == null) return ''
  const parts = new Intl.NumberFormat('pt-PT', {
    style: 'currency', currency: displayCurrency, maximumFractionDigits: 0,
  }).formatToParts(v * displayRate)
  return parts.map((p) => p.value).join('')
}

export const fmtPct = (v) =>
  v == null ? '—' : `${v >= 0 ? '+' : ''}${Number(v).toFixed(2)}%`

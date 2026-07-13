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

export const fmtEur = (v) =>
  v == null ? '—' : new Intl.NumberFormat('pt-PT', { style: 'currency', currency: 'EUR' }).format(v)

export const fmtPct = (v) =>
  v == null ? '—' : `${v >= 0 ? '+' : ''}${Number(v).toFixed(2)}%`

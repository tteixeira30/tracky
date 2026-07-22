import { createContext, useContext, useEffect, useState } from 'react'
import { api, getToken, setToken, clearToken, setOnUnauthorized, setDisplayCurrency } from '../api'
import { setCustomCategories } from '../categories'

const AuthContext = createContext(null)

export function useAuth() {
  return useContext(AuthContext)
}

/** Aplica a moeda base do utilizador à camada de apresentação. Devolve a moeda ativa. */
async function applyCurrency(fallback = 'EUR') {
  try {
    const info = await api.getCurrency()
    setDisplayCurrency(info.base, info.rate)
    return info.base
  } catch {
    setDisplayCurrency(fallback, 1)
    return fallback
  }
}

/** Carrega as categorias personalizadas do utilizador para o registo global. */
async function applyCategories() {
  try {
    setCustomCategories(await api.getExpenseCategories())
  } catch {
    setCustomCategories([])
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)
  const [baseCurrency, setBaseCurrency] = useState('EUR')

  useEffect(() => {
    setOnUnauthorized(() => setUser(null))
    if (!getToken()) {
      setLoading(false)
      return
    }
    api.me()
      .then(async (u) => {
        setUser(u)
        setBaseCurrency(await applyCurrency(u.baseCurrency))
        await applyCategories()
      })
      .catch(() => clearToken())
      .finally(() => setLoading(false))
  }, [])

  const finishAuth = async (res) => {
    setToken(res.token)
    setUser(res.user)
    setBaseCurrency(await applyCurrency(res.user.baseCurrency))
    await applyCategories()
    return res.user
  }

  const login = async (email, password) => finishAuth(await api.login({ email, password }))

  const register = async (name, email, password, inviteCode) =>
    finishAuth(await api.register({ name, email, password, inviteCode: inviteCode || null }))

  const logout = () => {
    clearToken()
    setUser(null)
    setDisplayCurrency('EUR', 1)
    setBaseCurrency('EUR')
    setCustomCategories([])
  }

  const changeCurrency = async (currency) => {
    const updated = await api.setCurrency(currency)
    setUser((u) => (u ? { ...u, baseCurrency: updated.baseCurrency } : u))
    setBaseCurrency(await applyCurrency(updated.baseCurrency))
  }

  return (
    <AuthContext.Provider value={{ user, loading, baseCurrency, login, register, logout, changeCurrency }}>
      {children}
    </AuthContext.Provider>
  )
}

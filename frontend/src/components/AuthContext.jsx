import { createContext, useContext, useEffect, useState } from 'react'
import { api, getToken, setToken, clearToken, setOnUnauthorized } from '../api'

const AuthContext = createContext(null)

export function useAuth() {
  return useContext(AuthContext)
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setOnUnauthorized(() => setUser(null))
    if (!getToken()) {
      setLoading(false)
      return
    }
    api.me()
      .then(setUser)
      .catch(() => clearToken())
      .finally(() => setLoading(false))
  }, [])

  const login = async (email, password) => {
    const res = await api.login({ email, password })
    setToken(res.token)
    setUser(res.user)
    return res.user
  }

  const register = async (name, email, password, inviteCode) => {
    const res = await api.register({ name, email, password, inviteCode: inviteCode || null })
    setToken(res.token)
    setUser(res.user)
    return res.user
  }

  const logout = () => {
    clearToken()
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

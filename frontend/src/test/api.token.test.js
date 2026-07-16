import { describe, expect, it } from 'vitest'
import { clearToken, getToken, setToken } from '../api'

describe('helpers de token (localStorage)', () => {
  it('sem sessão não há token', () => {
    expect(getToken()).toBeNull()
  })

  it('setToken guarda na chave tracky_token', () => {
    setToken('abc123')
    expect(localStorage.getItem('tracky_token')).toBe('abc123')
    expect(getToken()).toBe('abc123')
  })

  it('clearToken remove o token', () => {
    setToken('abc123')
    clearToken()
    expect(getToken()).toBeNull()
  })
})

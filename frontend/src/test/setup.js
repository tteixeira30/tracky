import '@testing-library/jest-dom'
import { afterEach } from 'vitest'
import { cleanup } from '@testing-library/react'
import { setDisplayCurrency } from '../api'

afterEach(() => {
  cleanup()
  localStorage.clear()
  // repõe o estado de moeda partilhado no módulo api.js
  setDisplayCurrency('EUR', 1)
})

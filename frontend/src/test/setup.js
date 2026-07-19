import '@testing-library/jest-dom'
import { createElement } from 'react'
import { afterEach, vi } from 'vitest'
import { cleanup } from '@testing-library/react'
import { setDisplayCurrency } from '../api'

// O Recharts precisa de dimensões reais de layout (inexistentes em jsdom) e não
// acrescenta valor aos testes de lógica das páginas. Substitui-se por elementos
// simples para os testes se focarem no comportamento, não no SVG dos gráficos.
vi.mock('recharts', () => {
  const Passthrough = ({ children }) => createElement('div', null, children)
  const names = [
    'ResponsiveContainer', 'AreaChart', 'Area', 'LineChart', 'Line',
    'PieChart', 'Pie', 'Cell', 'XAxis', 'YAxis', 'Tooltip', 'CartesianGrid', 'Legend',
  ]
  return Object.fromEntries(names.map((n) => [n, Passthrough]))
})

afterEach(() => {
  cleanup()
  localStorage.clear()
  // repõe o estado de moeda partilhado no módulo api.js
  setDisplayCurrency('EUR', 1)
})

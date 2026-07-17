import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import DashboardPage from '../pages/DashboardPage'
import { api } from '../api'

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, api: { getDashboard: vi.fn() } }
})
vi.mock('../components/AuthContext', () => ({ useAuth: () => ({ user: { name: 'Ana Silva' } }) }))
vi.mock('../components/ThemeContext', () => ({ useChartColors: () => ({ grid: '#000', axis: '#111' }) }))

const data = {
  netWorth: 1500, incomeMonth: '2025-06', monthlyIncome: 2000, unallocated: 300,
  totalInvested: 1000, investmentGain: 100, investmentGainPercent: 10,
  totalSaved: 500, totalGoalsTarget: 5000, goalsProgressPercent: 10,
  goalsCount: 2, goalsCompleted: 1,
  evolution: [{ date: '2025-01-01', value: 1000 }, { date: '2025-06-01', value: 1500 }],
  recentActivity: [], insights: [],
}

describe('DashboardPage', () => {
  beforeEach(() => vi.clearAllMocks())

  it('mostra o esqueleto enquanto carrega', () => {
    api.getDashboard.mockReturnValue(new Promise(() => {}))
    const { container } = render(<DashboardPage />)
    expect(container.querySelector('.skeleton')).toBeInTheDocument()
  })

  it('mostra o património, saudação e KPIs', async () => {
    api.getDashboard.mockResolvedValue(data)
    render(<DashboardPage />)

    await waitFor(() => expect(document.querySelector('.hero-value')).toBeInTheDocument())
    expect(document.querySelector('.hero-value')).toHaveTextContent(/1500,00/)
    expect(screen.getByText(/Olá, Ana/)).toBeInTheDocument()
    // KPIs
    const investKpi = document.querySelector('.kpi-card')
    expect(investKpi).toBeInTheDocument()
    expect(screen.getByText('Valor investido')).toBeInTheDocument()
    expect(screen.getByText('Poupado em objetivos')).toBeInTheDocument()
    expect(screen.getByText('1/2')).toBeInTheDocument() // objetivos concluídos/total
  })

  it('mostra estado de erro quando a API falha', async () => {
    api.getDashboard.mockRejectedValue(new Error('boom'))
    render(<DashboardPage />)

    await waitFor(() => expect(screen.getByText('Não foi possível carregar o painel')).toBeInTheDocument())
  })
})

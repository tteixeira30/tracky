import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import CalendarPage from '../pages/CalendarPage'
import { api } from '../api'

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    api: {
      getCalendar: vi.fn(), getUpcoming: vi.fn(), addCalendarEvent: vi.fn(),
      updateCalendarEvent: vi.fn(), deleteCalendarEvent: vi.fn(), setBalance: vi.fn(),
    },
  }
})
vi.mock('../components/Toast', () => ({
  useToast: () => ({ success: vi.fn(), error: vi.fn(), info: vi.fn() }),
}))

const monthData = (over = {}) => ({
  month: '2025-06',
  events: [{
    id: 1, name: 'Renda', category: 'BILL', inflow: false, amount: 800,
    frequency: 'MONTHLY', dayOfMonth: 1, eventDate: null, active: true,
  }],
  occurrences: [], inflows: 0, outflows: 800, net: -800, ...over,
})
const forecast = { startingBalance: 1000, hasBalance: true, days: 60, points: [], endBalance: 200 }

describe('CalendarPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.getUpcoming.mockResolvedValue(forecast)
  })

  it('mostra o esqueleto enquanto carrega', () => {
    api.getCalendar.mockReturnValue(new Promise(() => {}))
    const { container } = render(<CalendarPage />)
    expect(container.querySelector('.skeleton')).toBeInTheDocument()
  })

  it('mostra os eventos e os totais do mês', async () => {
    api.getCalendar.mockResolvedValue(monthData())
    render(<CalendarPage />)

    await waitFor(() => expect(screen.getByText('Renda')).toBeInTheDocument())
    expect(screen.getByText(/todo dia 1/)).toBeInTheDocument()
  })

  it('criar um evento mensal chama a API com o payload certo', async () => {
    api.getCalendar.mockResolvedValue(monthData({ events: [] }))
    api.addCalendarEvent.mockResolvedValue({})
    const user = userEvent.setup()
    render(<CalendarPage />)

    await waitFor(() => expect(screen.getByRole('button', { name: /Novo evento/ })).toBeInTheDocument())
    await user.click(screen.getByRole('button', { name: /Novo evento/ }))

    const dialog = screen.getByRole('dialog')
    await user.type(within(dialog).getByPlaceholderText('Ex: Salário, Renda, Netflix'), 'Luz')
    await user.type(within(dialog).getByPlaceholderText('0'), '60')
    await user.click(within(dialog).getByRole('button', { name: 'Guardar' }))

    await waitFor(() => expect(api.addCalendarEvent).toHaveBeenCalledTimes(1))
    expect(api.addCalendarEvent.mock.calls[0][0]).toMatchObject({ name: 'Luz', amount: 60 })
  })

  it('definir o saldo atual chama a API', async () => {
    api.getCalendar.mockResolvedValue(monthData())
    api.setBalance.mockResolvedValue({})
    const user = userEvent.setup()
    render(<CalendarPage />)

    await waitFor(() => expect(screen.getByRole('button', { name: /Saldo atual/ })).toBeInTheDocument())
    await user.click(screen.getByRole('button', { name: /Saldo atual/ }))

    const dialog = screen.getByRole('dialog')
    const input = within(dialog).getByPlaceholderText('Ex: 2500')
    await user.clear(input) // vem pré-preenchido com o saldo atual da previsão
    await user.type(input, '3000')
    await user.click(within(dialog).getByRole('button', { name: 'Guardar' }))

    await waitFor(() => expect(api.setBalance).toHaveBeenCalledWith(3000))
  })
})

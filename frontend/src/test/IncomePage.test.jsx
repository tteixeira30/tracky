import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import IncomePage from '../pages/IncomePage'
import { api } from '../api'

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    api: {
      getIncome: vi.fn(), setIncome: vi.fn(), addAllocation: vi.fn(),
      updateAllocation: vi.fn(), deleteAllocation: vi.fn(),
      addAllocationItem: vi.fn(), updateAllocationItem: vi.fn(), deleteAllocationItem: vi.fn(),
    },
  }
})
vi.mock('../components/Toast', () => ({
  useToast: () => ({ success: vi.fn(), error: vi.fn(), info: vi.fn() }),
}))

const income = (over = {}) => ({
  month: '2025-06', current: true, monthlyIncome: 2000,
  allocations: [{
    id: 1, name: 'Poupança', percentage: 20, fixedAmount: null, amount: 400,
    effectivePercentage: 20, items: [], itemsTotal: 0, color: '#33aaff',
  }],
  totalAllocated: 400, totalPercentage: 20, unallocated: 1600, availableMonths: ['2025-06'],
  copiedFrom: null, ...over,
})

describe('IncomePage', () => {
  beforeEach(() => vi.clearAllMocks())

  it('mostra o esqueleto enquanto carrega', () => {
    api.getIncome.mockReturnValue(new Promise(() => {}))
    const { container } = render(<IncomePage />)
    expect(container.querySelector('.skeleton')).toBeInTheDocument()
  })

  it('mostra o rendimento e as categorias', async () => {
    api.getIncome.mockResolvedValue(income())
    render(<IncomePage />)

    await waitFor(() => expect(screen.getByText('Poupança')).toBeInTheDocument())
    expect(screen.getAllByText(/2000,00/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/20%/).length).toBeGreaterThan(0)
  })

  it('estado vazio permite criar a primeira categoria', async () => {
    api.getIncome.mockResolvedValue(income({ allocations: [], totalAllocated: 0, unallocated: 2000 }))
    render(<IncomePage />)

    await waitFor(() =>
      expect(screen.getByRole('button', { name: /Criar categoria/ })).toBeInTheDocument())
  })

  it('adicionar categoria por percentagem chama a API', async () => {
    api.getIncome.mockResolvedValue(income({ allocations: [], totalAllocated: 0, unallocated: 2000 }))
    api.addAllocation.mockResolvedValue(income())
    const user = userEvent.setup()
    render(<IncomePage />)

    await waitFor(() => expect(screen.getByRole('button', { name: /Criar categoria/ })).toBeInTheDocument())
    await user.click(screen.getByRole('button', { name: /Criar categoria/ }))

    const dialog = screen.getByRole('dialog')
    await user.type(within(dialog).getByPlaceholderText('Ex: Poupança, Renda…'), 'Renda')
    await user.type(within(dialog).getByPlaceholderText('Ex: 30'), '25')
    await user.click(within(dialog).getByRole('button', { name: 'Adicionar' }))

    await waitFor(() => expect(api.addAllocation).toHaveBeenCalledTimes(1))
    expect(api.addAllocation.mock.calls[0][0]).toMatchObject({ name: 'Renda', percentage: 25 })
  })
})

import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import GoalsPage from '../pages/GoalsPage'
import { api } from '../api'

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    api: {
      getGoals: vi.fn(), addGoal: vi.fn(), updateGoal: vi.fn(),
      contributeGoal: vi.fn(), deleteGoal: vi.fn(), applyDeposits: vi.fn(),
    },
  }
})
// silencia os toasts (não interessam à asserção e poluem o DOM)
vi.mock('../components/Toast', () => ({
  useToast: () => ({ success: vi.fn(), error: vi.fn(), info: vi.fn() }),
}))

const goal = (over = {}) => ({
  id: 1, name: 'Fundo de emergência', targetAmount: 1000, monthlyAllocation: 100,
  savedAmount: 300, progressPercent: 30, autoDeposit: false, contributionDay: 1, ...over,
})

describe('GoalsPage', () => {
  beforeEach(() => vi.clearAllMocks())

  it('mostra o esqueleto enquanto carrega', () => {
    api.getGoals.mockReturnValue(new Promise(() => {}))
    const { container } = render(<GoalsPage />)
    expect(container.querySelector('.skeleton')).toBeInTheDocument()
  })

  it('lista os objetivos com progresso', async () => {
    api.getGoals.mockResolvedValue([goal()])
    render(<GoalsPage />)

    await waitFor(() => expect(screen.getByText('Fundo de emergência')).toBeInTheDocument())
    const card = document.querySelector('.goal-card')
    expect(within(card).getByText(/300,00/)).toBeInTheDocument()
    expect(within(card).getByText(/30.0%/)).toBeInTheDocument()
  })

  it('criar um objetivo envia os valores convertidos e recarrega', async () => {
    api.getGoals.mockResolvedValueOnce([]).mockResolvedValueOnce([goal()])
    api.addGoal.mockResolvedValue({})
    const user = userEvent.setup()
    render(<GoalsPage />)

    await waitFor(() => expect(screen.getByRole('button', { name: /Novo objetivo/ })).toBeInTheDocument())
    await user.click(screen.getByRole('button', { name: /Novo objetivo/ }))

    await user.type(screen.getByPlaceholderText('Ex: Fundo de emergência'), 'Carro')
    await user.type(screen.getByPlaceholderText('Ex: 10000'), '5000')
    await user.type(screen.getByPlaceholderText('Ex: 300'), '200')
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Criar objetivo' }))

    await waitFor(() => expect(api.addGoal).toHaveBeenCalledTimes(1))
    expect(api.addGoal).toHaveBeenCalledWith(expect.objectContaining({
      name: 'Carro', targetAmount: 5000, monthlyAllocation: 200,
    }))
  })

  it('não cria com campos em falta (não chama a API)', async () => {
    api.getGoals.mockResolvedValue([])
    const user = userEvent.setup()
    render(<GoalsPage />)

    await waitFor(() => expect(screen.getByRole('button', { name: /Novo objetivo/ })).toBeInTheDocument())
    await user.click(screen.getByRole('button', { name: /Novo objetivo/ }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Criar objetivo' }))

    expect(api.addGoal).not.toHaveBeenCalled()
  })

  it('contribuir para um objetivo chama a API com o valor', async () => {
    api.getGoals.mockResolvedValue([goal()])
    api.contributeGoal.mockResolvedValue({ progressPercent: 50 })
    const user = userEvent.setup()
    render(<GoalsPage />)

    await waitFor(() => expect(screen.getByText('Fundo de emergência')).toBeInTheDocument())
    const card = document.querySelector('.goal-card')
    await user.type(within(card).getByPlaceholderText('Valor'), '200')
    await user.click(within(card).getByRole('button', { name: /Contribuir/ }))

    await waitFor(() => expect(api.contributeGoal).toHaveBeenCalledWith(1, 200))
  })
})

import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import App from '../App'
import { useAuth } from '../components/AuthContext'

// AuthProvider vira passthrough; useAuth é controlado por teste
vi.mock('../components/AuthContext', () => ({
  AuthProvider: ({ children }) => children,
  useAuth: vi.fn(),
}))
// páginas substituídas por stubs para isolar o shell (sem chamadas à API)
vi.mock('../pages/DashboardPage', () => ({ default: () => <div>PÁGINA_PAINEL</div> }))
vi.mock('../pages/IncomePage', () => ({ default: () => <div>PÁGINA_RENDIMENTO</div> }))
vi.mock('../pages/InvestmentsPage', () => ({ default: () => <div>PÁGINA_INVEST</div> }))
vi.mock('../pages/GoalsPage', () => ({ default: () => <div>PÁGINA_OBJETIVOS</div> }))
vi.mock('../pages/CalendarPage', () => ({ default: () => <div>PÁGINA_CALENDARIO</div> }))
vi.mock('../pages/AchievementsPage', () => ({ default: () => <div>PÁGINA_CONQUISTAS</div> }))
vi.mock('../pages/AuthPage', () => ({ default: () => <div>PÁGINA_AUTH</div> }))

const authed = {
  user: { name: 'Ana Silva', email: 'ana@ex.com' },
  loading: false, baseCurrency: 'EUR',
  logout: vi.fn(), changeCurrency: vi.fn(),
}

describe('App / Shell', () => {
  beforeEach(() => vi.clearAllMocks())

  it('mostra o esqueleto enquanto a sessão carrega', () => {
    useAuth.mockReturnValue({ ...authed, loading: true, user: null })
    const { container } = render(<App />)
    expect(container.querySelector('.skeleton')).toBeInTheDocument()
  })

  it('sem sessão mostra a página de autenticação', () => {
    useAuth.mockReturnValue({ ...authed, user: null })
    render(<App />)
    expect(screen.getByText('PÁGINA_AUTH')).toBeInTheDocument()
  })

  it('com sessão mostra o painel por omissão e as iniciais do utilizador', () => {
    useAuth.mockReturnValue(authed)
    render(<App />)
    expect(screen.getByText('PÁGINA_PAINEL')).toBeInTheDocument()
    expect(screen.getByText('AS')).toBeInTheDocument() // iniciais
    expect(screen.getByText('ana@ex.com')).toBeInTheDocument()
  })

  it('clicar num separador troca a página apresentada', async () => {
    useAuth.mockReturnValue(authed)
    const user = userEvent.setup()
    render(<App />)

    // o mesmo separador existe na sidebar e na bottom-nav — clica no primeiro
    await user.click(screen.getAllByRole('button', { name: /Investimentos/ })[0])

    expect(screen.getByText('PÁGINA_INVEST')).toBeInTheDocument()
  })

  it('o botão de terminar sessão chama logout', async () => {
    useAuth.mockReturnValue(authed)
    const user = userEvent.setup()
    render(<App />)

    await user.click(screen.getByRole('button', { name: 'Perfil e definições' }))
    await user.click(screen.getByRole('menuitem', { name: 'Terminar sessão' }))
    expect(authed.logout).toHaveBeenCalledOnce()
  })
})

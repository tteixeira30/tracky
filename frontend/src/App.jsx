import { useState } from 'react'
import DashboardPage from './pages/DashboardPage'
import IncomePage from './pages/IncomePage'
import InvestmentsPage from './pages/InvestmentsPage'
import GoalsPage from './pages/GoalsPage'
import CalendarPage from './pages/CalendarPage'
import ExpensesPage from './pages/ExpensesPage'
import AchievementsPage from './pages/AchievementsPage'
import AuthPage from './pages/AuthPage'
import { ToastProvider } from './components/Toast'
import { AuthProvider, useAuth } from './components/AuthContext'
import { ThemeProvider, useTheme } from './components/ThemeContext'
import Dropdown from './components/Dropdown'
import { IconLogo, IconGrid, IconWallet, IconTrendingUp, IconTarget, IconCalendar, IconReceipt, IconTrophy, IconLogout, IconSun, IconMoon, IconEye, IconEyeOff } from './components/Icons'
import { setPrivacyMode } from './api'

const TABS = [
  { id: 'dashboard', label: 'Painel', icon: IconGrid },
  { id: 'income', label: 'Rendimento', icon: IconWallet },
  { id: 'expenses', label: 'Despesas', icon: IconReceipt },
  { id: 'investments', label: 'Investimentos', icon: IconTrendingUp },
  { id: 'goals', label: 'Objetivos', icon: IconTarget },
  { id: 'calendar', label: 'Calendário', icon: IconCalendar },
  { id: 'achievements', label: 'Conquistas', icon: IconTrophy },
]

const CURRENCIES = [
  { code: 'EUR', symbol: '€', name: 'Euro' },
  { code: 'USD', symbol: '$', name: 'Dólar EUA' },
  { code: 'GBP', symbol: '£', name: 'Libra' },
  { code: 'BRL', symbol: 'R$', name: 'Real' },
  { code: 'CHF', symbol: 'Fr', name: 'Franco suíço' },
  { code: 'CAD', symbol: 'C$', name: 'Dólar canadiano' },
  { code: 'AUD', symbol: 'A$', name: 'Dólar australiano' },
  { code: 'JPY', symbol: '¥', name: 'Iene' },
]

function ThemeToggle() {
  const { theme, toggle } = useTheme()
  const dark = theme === 'dark'
  return (
    <button className="theme-toggle" onClick={toggle} role="switch" aria-checked={dark}
            aria-label={dark ? 'Mudar para tema claro' : 'Mudar para tema escuro'}
            title={dark ? 'Tema claro' : 'Tema escuro'}>
      <span className={`tt-opt ${dark ? 'active' : ''}`}><IconMoon size={14} /></span>
      <span className={`tt-opt ${!dark ? 'active' : ''}`}><IconSun size={14} /></span>
    </button>
  )
}

function Shell() {
  const { user, loading, logout, baseCurrency, changeCurrency } = useAuth()
  const [tab, setTab] = useState('dashboard')
  const [privacy, setPrivacy] = useState(() => {
    const on = localStorage.getItem('tracky_privacy') === '1'
    setPrivacyMode(on)
    return on
  })

  const togglePrivacy = () => {
    const next = !privacy
    setPrivacy(next)
    setPrivacyMode(next)
    localStorage.setItem('tracky_privacy', next ? '1' : '0')
  }

  if (loading) {
    return (
      <div className="auth-wrap">
        <div className="skeleton" style={{ width: 380, height: 420, borderRadius: 18 }} />
      </div>
    )
  }

  if (!user) return <AuthPage />

  const initials = user.name.split(' ').filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join('')

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <IconLogo size={34} />
          <div>
            <h1>Trac<span>ky</span></h1>
            <small>Finanças pessoais</small>
          </div>
        </div>
        <nav className="nav">
          {TABS.map((t) => (
            <button
              key={t.id}
              className={`nav-item ${tab === t.id ? 'active' : ''}`}
              onClick={() => setTab(t.id)}
            >
              <t.icon size={18} />
              <span>{t.label}</span>
            </button>
          ))}
        </nav>
        <div className="sidebar-foot">
          <div className="currency-select">
            <span>Moeda base</span>
            <Dropdown value={baseCurrency} onChange={changeCurrency}
                      options={CURRENCIES.map((c) => ({ value: c.code, label: `${c.code} · ${c.symbol}` }))} />
          </div>
          <div className="theme-select">
            <span>Aparência</span>
            <ThemeToggle />
          </div>
          <div className="theme-select">
            <span>Privacidade</span>
            <button className="theme-toggle" onClick={togglePrivacy} role="switch" aria-checked={privacy}
                    aria-label={privacy ? 'Mostrar valores' : 'Esconder valores'}
                    title={privacy ? 'Mostrar valores' : 'Esconder valores'}>
              <span className={`tt-opt ${privacy ? 'active' : ''}`}><IconEyeOff size={14} /></span>
              <span className={`tt-opt ${!privacy ? 'active' : ''}`}><IconEye size={14} /></span>
            </button>
          </div>
          <div className="user-chip">
            <span className="user-avatar">{initials}</span>
            <div className="user-info">
              <strong>{user.name}</strong>
              <small>{user.email}</small>
            </div>
            <button className="icon-btn" onClick={logout} title="Terminar sessão" aria-label="Terminar sessão">
              <IconLogout size={16} />
            </button>
          </div>
          <div className="live-note">
            <span className="dot" />Cotações em tempo real<br />
            Yahoo Finance · CoinGecko
          </div>
        </div>
      </aside>
      <main className="main" key={`${baseCurrency}-${privacy ? 'p1' : 'p0'}`}>
        {tab === 'dashboard' && <DashboardPage />}
        {tab === 'income' && <IncomePage />}
        {tab === 'expenses' && <ExpensesPage />}
        {tab === 'investments' && <InvestmentsPage />}
        {tab === 'goals' && <GoalsPage />}
        {tab === 'calendar' && <CalendarPage />}
        {tab === 'achievements' && <AchievementsPage />}
      </main>
      <nav className="bottom-nav" aria-label="Navegação">
        {TABS.map((t) => (
          <button
            key={t.id}
            className={tab === t.id ? 'active' : ''}
            onClick={() => setTab(t.id)}
          >
            <t.icon size={21} />
            <span>{t.label}</span>
          </button>
        ))}
      </nav>
    </div>
  )
}

export default function App() {
  return (
    <ThemeProvider>
      <ToastProvider>
        <AuthProvider>
          <Shell />
        </AuthProvider>
      </ToastProvider>
    </ThemeProvider>
  )
}

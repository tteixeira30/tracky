import { useState } from 'react'
import IncomePage from './pages/IncomePage'
import InvestmentsPage from './pages/InvestmentsPage'
import GoalsPage from './pages/GoalsPage'
import AuthPage from './pages/AuthPage'
import { ToastProvider } from './components/Toast'
import { AuthProvider, useAuth } from './components/AuthContext'
import { IconLogo, IconWallet, IconTrendingUp, IconTarget, IconLogout } from './components/Icons'

const TABS = [
  { id: 'income', label: 'Rendimento', icon: IconWallet },
  { id: 'investments', label: 'Investimentos', icon: IconTrendingUp },
  { id: 'goals', label: 'Objetivos', icon: IconTarget },
]

function Shell() {
  const { user, loading, logout } = useAuth()
  const [tab, setTab] = useState('income')

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
      <main className="main">
        {tab === 'income' && <IncomePage />}
        {tab === 'investments' && <InvestmentsPage />}
        {tab === 'goals' && <GoalsPage />}
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
    <ToastProvider>
      <AuthProvider>
        <Shell />
      </AuthProvider>
    </ToastProvider>
  )
}

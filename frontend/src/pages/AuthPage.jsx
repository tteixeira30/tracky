import { useState } from 'react'
import { useAuth } from '../components/AuthContext'
import { useToast } from '../components/Toast'
import { IconLogo } from '../components/Icons'

export default function AuthPage() {
  const { login, register } = useAuth()
  const toast = useToast()
  const [mode, setMode] = useState('login')
  const [form, setForm] = useState({ name: '', email: '', password: '', inviteCode: '' })
  const [busy, setBusy] = useState(false)

  const isLogin = mode === 'login'

  const submit = async (e) => {
    e.preventDefault()
    if (!form.email.trim() || !form.password || (!isLogin && !form.name.trim())) {
      toast.error('Campos em falta', 'Preenche todos os campos para continuar.')
      return
    }
    setBusy(true)
    try {
      if (isLogin) {
        const user = await login(form.email.trim(), form.password)
        toast.success(`Olá, ${user.name.split(' ')[0]}!`, 'Sessão iniciada com sucesso.')
      } else {
        const user = await register(form.name.trim(), form.email.trim(), form.password, form.inviteCode.trim())
        toast.success(`Bem-vindo, ${user.name.split(' ')[0]}!`, 'Conta criada com sucesso.')
      }
    } catch (err) {
      toast.error(isLogin ? 'Erro ao iniciar sessão' : 'Erro ao criar conta', err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth-wrap">
      <div className="auth-card">
        <div className="auth-brand">
          <IconLogo size={44} />
          <h1>Vault<span>rack</span></h1>
          <p>Gere o teu rendimento, investimentos e objetivos num só lugar.</p>
        </div>

        <div className="auth-tabs">
          <button className={isLogin ? 'active' : ''} onClick={() => setMode('login')} type="button">
            Iniciar sessão
          </button>
          <button className={!isLogin ? 'active' : ''} onClick={() => setMode('register')} type="button">
            Criar conta
          </button>
        </div>

        <form onSubmit={submit} className="auth-form">
          {!isLogin && (
            <div className="field">
              <label>Nome</label>
              <input placeholder="O teu nome" autoFocus value={form.name}
                     onChange={(e) => setForm({ ...form, name: e.target.value })} />
            </div>
          )}
          <div className="field">
            <label>Email</label>
            <input type="email" placeholder="exemplo@email.com" autoFocus={isLogin} value={form.email}
                   onChange={(e) => setForm({ ...form, email: e.target.value })} />
          </div>
          <div className="field">
            <label>Palavra-passe</label>
            <input type="password" placeholder={isLogin ? 'A tua palavra-passe' : 'Mínimo 6 caracteres'}
                   value={form.password}
                   onChange={(e) => setForm({ ...form, password: e.target.value })} />
          </div>
          {!isLogin && (
            <div className="field">
              <label>Código de convite <span className="dim">(se aplicável)</span></label>
              <input placeholder="Deixa vazio se não tiveres" value={form.inviteCode}
                     onChange={(e) => setForm({ ...form, inviteCode: e.target.value })} />
            </div>
          )}
          <button className="btn auth-submit" type="submit" disabled={busy}>
            {busy ? 'Aguarda…' : isLogin ? 'Entrar' : 'Criar conta'}
          </button>
        </form>

        <p className="auth-switch">
          {isLogin ? 'Ainda não tens conta?' : 'Já tens conta?'}{' '}
          <button type="button" onClick={() => setMode(isLogin ? 'register' : 'login')}>
            {isLogin ? 'Regista-te' : 'Inicia sessão'}
          </button>
        </p>
      </div>
    </div>
  )
}

import { createContext, useCallback, useContext, useRef, useState } from 'react'
import { IconAlert, IconCheck, IconInfo, IconX } from './Icons'

const ToastContext = createContext(null)

export function useToast() {
  return useContext(ToastContext)
}

const ICONS = {
  success: <IconCheck size={17} />,
  error: <IconAlert size={17} />,
  info: <IconInfo size={17} />,
}

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([])
  const nextId = useRef(1)

  const dismiss = useCallback((id) => {
    setToasts((list) => list.map((t) => (t.id === id ? { ...t, leaving: true } : t)))
    setTimeout(() => setToasts((list) => list.filter((t) => t.id !== id)), 220)
  }, [])

  const push = useCallback((type, title, message) => {
    const id = nextId.current++
    setToasts((list) => [...list.slice(-3), { id, type, title, message }])
    setTimeout(() => dismiss(id), 4500)
  }, [dismiss])

  const toast = {
    success: (title, message) => push('success', title, message),
    error: (title, message) => push('error', title, message),
    info: (title, message) => push('info', title, message),
  }

  return (
    <ToastContext.Provider value={toast}>
      {children}
      <div className="toast-stack" role="status" aria-live="polite">
        {toasts.map((t) => (
          <div key={t.id} className={`toast toast-${t.type} ${t.leaving ? 'toast-leave' : ''}`}>
            <span className="toast-icon">{ICONS[t.type]}</span>
            <div className="toast-text">
              <strong>{t.title}</strong>
              {t.message && <span>{t.message}</span>}
            </div>
            <button className="toast-close" onClick={() => dismiss(t.id)} aria-label="Fechar">
              <IconX size={14} />
            </button>
            <span className="toast-progress" />
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

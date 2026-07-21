import { useEffect, useState } from 'react'
import { IconAlert, IconX } from './Icons'

export default function Modal({ open, title, subtitle, onClose, children, footer, width = 540, dirty = false }) {
  // quando o form tem alterações por guardar, pedir confirmação antes de descartar
  const [confirmDiscard, setConfirmDiscard] = useState(false)

  // tentativa de fechar "acidental" (clique fora, Escape, X): se estiver sujo, confirmar
  const requestClose = () => {
    if (dirty) setConfirmDiscard(true)
    else onClose()
  }

  useEffect(() => {
    if (!open) return
    const onKey = (e) => {
      if (e.key !== 'Escape') return
      // com a confirmação aberta, Escape cancela apenas a confirmação
      if (confirmDiscard) setConfirmDiscard(false)
      else requestClose()
    }
    document.addEventListener('keydown', onKey)
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = ''
    }
  }, [open, onClose, dirty, confirmDiscard])

  // ao fechar (ou reabrir) o modal, limpar o estado da confirmação
  useEffect(() => { if (!open) setConfirmDiscard(false) }, [open])

  if (!open) return null

  return (
    <div className="modal-overlay" onMouseDown={(e) => e.target === e.currentTarget && requestClose()}>
      <div className="modal" style={{ maxWidth: width }} role="dialog" aria-modal="true">
        <div className="modal-head">
          <div>
            <h3>{title}</h3>
            {subtitle && <p className="modal-subtitle">{subtitle}</p>}
          </div>
          <button className="icon-btn" onClick={requestClose} aria-label="Fechar">
            <IconX size={18} />
          </button>
        </div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-foot">{footer}</div>}
      </div>

      {confirmDiscard && (
        <div className="modal-overlay" onMouseDown={(e) => e.target === e.currentTarget && setConfirmDiscard(false)}>
          <div className="modal modal-confirm" role="alertdialog" aria-modal="true">
            <div className="confirm-icon"><IconAlert size={26} /></div>
            <h3>Descartar alterações?</h3>
            <p>Tens dados por guardar neste formulário. Se saíres agora, perdes o que escreveste.</p>
            <div className="confirm-actions">
              <button className="btn ghost" onClick={() => setConfirmDiscard(false)}>Continuar a editar</button>
              <button className="btn danger" onClick={() => { setConfirmDiscard(false); onClose() }}>Descartar</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export function ConfirmDialog({ open, title, message, confirmLabel = 'Eliminar', onConfirm, onCancel, busy }) {
  useEffect(() => {
    if (!open) return
    const onKey = (e) => e.key === 'Escape' && onCancel()
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open, onCancel])

  if (!open) return null
  return (
    <div className="modal-overlay" onMouseDown={(e) => e.target === e.currentTarget && onCancel()}>
      <div className="modal modal-confirm" role="alertdialog" aria-modal="true">
        <div className="confirm-icon"><IconAlert size={26} /></div>
        <h3>{title}</h3>
        <p>{message}</p>
        <div className="confirm-actions">
          <button className="btn ghost" onClick={onCancel} disabled={busy}>Cancelar</button>
          <button className="btn danger" onClick={onConfirm} disabled={busy}>
            {busy ? 'A eliminar…' : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}

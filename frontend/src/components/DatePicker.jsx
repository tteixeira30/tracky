import { useEffect, useRef, useState } from 'react'
import { IconCalendar, IconChevronLeft, IconChevronRight } from './Icons'

const WEEKDAYS = ['Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb', 'Dom']

const pad = (n) => String(n).padStart(2, '0')
const iso = (y, m, d) => `${y}-${pad(m)}-${pad(d)}`
const todayIso = () => {
  const t = new Date()
  return iso(t.getFullYear(), t.getMonth() + 1, t.getDate())
}
const fmtDisplay = (v) => {
  if (!v) return ''
  const [y, m, d] = v.split('-')
  return `${d}/${m}/${y}`
}
const fmtMonth = (view) => {
  const [y, m] = view.split('-').map(Number)
  const s = new Date(y, m - 1, 1).toLocaleDateString('pt-PT', { month: 'long', year: 'numeric' })
  return s.charAt(0).toUpperCase() + s.slice(1)
}

/** Seletor de data com o visual da app (substitui o <input type="date"> nativo). */
export default function DatePicker({ value, onChange, placeholder = 'Seleciona a data' }) {
  const [open, setOpen] = useState(false)
  const [view, setView] = useState(() => (value || todayIso()).slice(0, 7))
  const ref = useRef(null)

  useEffect(() => {
    if (!open) return
    const onDown = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false) }
    document.addEventListener('mousedown', onDown)
    return () => document.removeEventListener('mousedown', onDown)
  }, [open])

  const toggle = () => {
    if (!open) setView((value || todayIso()).slice(0, 7))
    setOpen((o) => !o)
  }
  const shift = (delta) => {
    const [y, m] = view.split('-').map(Number)
    const d = new Date(y, m - 1 + delta, 1)
    setView(`${d.getFullYear()}-${pad(d.getMonth() + 1)}`)
  }
  const pick = (day) => {
    const [y, m] = view.split('-').map(Number)
    onChange(iso(y, m, day))
    setOpen(false)
  }

  const [y, m] = view.split('-').map(Number)
  const lead = (new Date(y, m - 1, 1).getDay() + 6) % 7
  const days = new Date(y, m, 0).getDate()
  const cells = [...Array(lead).fill(null), ...Array.from({ length: days }, (_, i) => i + 1)]
  const today = todayIso()

  return (
    <div className={`datepicker ${open ? 'open' : ''}`} ref={ref}>
      <button type="button" className="dp-trigger" onClick={toggle}>
        <span className={value ? '' : 'dp-placeholder'}>{value ? fmtDisplay(value) : placeholder}</span>
        <IconCalendar size={16} />
      </button>

      {open && (
        <div className="dp-pop">
          <div className="dp-head">
            <button type="button" className="icon-btn" onClick={() => shift(-1)} aria-label="Mês anterior"><IconChevronLeft size={17} /></button>
            <span className="dp-month">{fmtMonth(view)}</span>
            <button type="button" className="icon-btn" onClick={() => shift(1)} aria-label="Mês seguinte"><IconChevronRight size={17} /></button>
          </div>
          <div className="dp-grid">
            {WEEKDAYS.map((w) => <span key={w} className="dp-wd">{w}</span>)}
            {cells.map((day, i) => {
              if (!day) return <span key={i} className="dp-day empty" />
              const cellIso = iso(y, m, day)
              return (
                <button key={i} type="button"
                        className={`dp-day ${cellIso === value ? 'selected' : ''} ${cellIso === today ? 'today' : ''}`}
                        onClick={() => pick(day)}>
                  {day}
                </button>
              )
            })}
          </div>
          <div className="dp-foot">
            <button type="button" onClick={() => { onChange(''); setOpen(false) }}>Limpar</button>
            <button type="button" onClick={() => { const t = todayIso(); onChange(t); setView(t.slice(0, 7)); setOpen(false) }}>Hoje</button>
          </div>
        </div>
      )}
    </div>
  )
}

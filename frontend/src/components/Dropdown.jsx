import { useEffect, useRef, useState } from 'react'
import { IconChevronRight } from './Icons'

/** Dropdown com o visual da app (substitui o <select> nativo). */
export default function Dropdown({ value, onChange, options = [], placeholder = 'Seleciona…' }) {
  const [open, setOpen] = useState(false)
  const ref = useRef(null)

  useEffect(() => {
    if (!open) return
    const onDown = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false) }
    const onKey = (e) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  const selected = options.find((o) => o.value === value)

  const pick = (val) => {
    onChange(val)
    setOpen(false)
  }

  return (
    <div className={`dropdown ${open ? 'open' : ''}`} ref={ref}>
      <button type="button" className="dd-trigger" onClick={() => setOpen((o) => !o)} aria-expanded={open} aria-haspopup="listbox">
        <span className={selected ? '' : 'dd-placeholder'}>{selected ? selected.label : placeholder}</span>
        <IconChevronRight size={16} className="dd-chevron" />
      </button>

      {open && (
        <div className="dd-pop" role="listbox">
          {options.map((o) => (
            <button key={o.value} type="button" role="option" aria-selected={o.value === value}
                    className={`dd-option ${o.value === value ? 'selected' : ''}`}
                    onClick={() => pick(o.value)}>
              {o.label}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

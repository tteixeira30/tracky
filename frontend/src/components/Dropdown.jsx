import { useEffect, useRef, useState } from 'react'
import { IconChevronRight } from './Icons'

const POP_MAX = 300   // altura máxima do menu (chega para as 8 moedas sem scroll interno)

/** Dropdown com o visual da app (substitui o <select> nativo). */
export default function Dropdown({ value, onChange, options = [], placeholder = 'Seleciona…' }) {
  const [open, setOpen] = useState(false)
  const [dropUp, setDropUp] = useState(false)
  const [maxH, setMaxH] = useState(POP_MAX)
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

  // decide abrir para cima/baixo consoante o espaço disponível (evita cortar opções
  // quando o dropdown está no fundo do ecrã, ex: seletor de moeda na barra lateral)
  const toggle = () => {
    setOpen((wasOpen) => {
      if (!wasOpen && ref.current) {
        const rect = ref.current.getBoundingClientRect()
        const below = window.innerHeight - rect.bottom - 8
        const above = rect.top - 8
        const up = below < Math.min(POP_MAX, above)
        setDropUp(up)
        setMaxH(Math.max(140, Math.min(POP_MAX, up ? above : below)))
      }
      return !wasOpen
    })
  }

  const pick = (val) => {
    onChange(val)
    setOpen(false)
  }

  return (
    <div className={`dropdown ${open ? 'open' : ''} ${dropUp ? 'up' : ''}`} ref={ref}>
      <button type="button" className="dd-trigger" onClick={toggle} aria-expanded={open} aria-haspopup="listbox">
        <span className={selected ? '' : 'dd-placeholder'}>{selected ? selected.label : placeholder}</span>
        <IconChevronRight size={16} className="dd-chevron" />
      </button>

      {open && (
        <div className="dd-pop" role="listbox" style={{ maxHeight: maxH }}>
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

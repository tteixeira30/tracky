import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ThemeProvider, useTheme, useChartColors } from '../components/ThemeContext'

function Demo() {
  const { theme, toggle } = useTheme()
  const colors = useChartColors()
  return (
    <div>
      <span data-testid="theme">{theme}</span>
      <span data-testid="grid">{colors.grid}</span>
      <button onClick={toggle}>alternar</button>
    </div>
  )
}

const renderThemed = () => render(<ThemeProvider><Demo /></ThemeProvider>)

describe('ThemeContext', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.removeAttribute('data-theme')
    // por omissão o sistema pede tema escuro
    window.matchMedia = vi.fn().mockReturnValue({ matches: false })
  })
  afterEach(() => { vi.restoreAllMocks() })

  it('usa o tema guardado no localStorage', () => {
    localStorage.setItem('tracky_theme', 'light')
    renderThemed()
    expect(screen.getByTestId('theme')).toHaveTextContent('light')
    expect(document.documentElement.dataset.theme).toBe('light')
  })

  it('sem preferência guardada segue a preferência do sistema (claro)', () => {
    window.matchMedia = vi.fn().mockReturnValue({ matches: true }) // prefers light
    renderThemed()
    expect(screen.getByTestId('theme')).toHaveTextContent('light')
  })

  it('sem preferência nenhuma cai para o tema escuro', () => {
    renderThemed()
    expect(screen.getByTestId('theme')).toHaveTextContent('dark')
  })

  it('toggle alterna o tema e persiste no localStorage', async () => {
    localStorage.setItem('tracky_theme', 'dark')
    renderThemed()
    await userEvent.click(screen.getByRole('button', { name: 'alternar' }))
    expect(screen.getByTestId('theme')).toHaveTextContent('light')
    expect(localStorage.getItem('tracky_theme')).toBe('light')
  })

  it('useChartColors devolve as cores do tema ativo', async () => {
    localStorage.setItem('tracky_theme', 'dark')
    renderThemed()
    expect(screen.getByTestId('grid')).toHaveTextContent('#232936') // grelha do tema escuro
    await userEvent.click(screen.getByRole('button', { name: 'alternar' }))
    expect(screen.getByTestId('grid')).toHaveTextContent('#e2e6ee') // grelha do tema claro
  })
})

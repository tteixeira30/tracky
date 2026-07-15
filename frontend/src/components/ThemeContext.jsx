import { createContext, useContext, useEffect, useState } from 'react'

const ThemeContext = createContext(null)
const STORAGE_KEY = 'tracky_theme'

/** Tema inicial: preferência guardada > preferência do sistema > escuro. */
function getInitialTheme() {
  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved === 'light' || saved === 'dark') return saved
  return window.matchMedia?.('(prefers-color-scheme: light)').matches ? 'light' : 'dark'
}

export function ThemeProvider({ children }) {
  const [theme, setTheme] = useState(getInitialTheme)

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    localStorage.setItem(STORAGE_KEY, theme)
  }, [theme])

  const toggle = () => setTheme((t) => (t === 'dark' ? 'light' : 'dark'))

  return (
    <ThemeContext.Provider value={{ theme, toggle }}>
      {children}
    </ThemeContext.Provider>
  )
}

export function useTheme() {
  return useContext(ThemeContext)
}

// Recharts não lê CSS custom properties em atributos SVG — as cores dos gráficos
// (grelha e eixos) têm de ser passadas explicitamente consoante o tema ativo.
const CHART_COLORS = {
  dark: { grid: '#232936', axis: '#5c6478' },
  light: { grid: '#e2e6ee', axis: '#8b93a7' },
}

export function useChartColors() {
  const { theme } = useTheme()
  return CHART_COLORS[theme] ?? CHART_COLORS.dark
}

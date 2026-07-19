// Categorias de movimentos — rótulos PT-PT e cores. Partilhado entre a página de
// Despesas e o painel para evitar duplicar a definição. Tem de acompanhar o enum
// Transaction.Category do backend.
export const CATEGORY_META = {
  INCOME: { label: 'Rendimento', color: 'var(--green)' },
  GROCERIES: { label: 'Supermercado', color: '#f59e0b' },
  RESTAURANT: { label: 'Restauração', color: '#fb7185' },
  TRANSPORT: { label: 'Transportes', color: 'var(--cyan)' },
  HOUSING: { label: 'Casa & contas', color: '#a78bfa' },
  SUBSCRIPTION: { label: 'Subscrições', color: '#818cf8' },
  SHOPPING: { label: 'Compras', color: '#f472b6' },
  HEALTH: { label: 'Saúde', color: '#34d399' },
  LEISURE: { label: 'Lazer', color: '#fbbf24' },
  TRANSFER: { label: 'Transferências', color: '#94a3b8' },
  OTHER: { label: 'Outros', color: 'var(--text-dim)' },
}

export const CATEGORIES = Object.keys(CATEGORY_META)
export const catLabel = (c) => (CATEGORY_META[c] || CATEGORY_META.OTHER).label
export const catColor = (c) => (CATEGORY_META[c] || CATEGORY_META.OTHER).color

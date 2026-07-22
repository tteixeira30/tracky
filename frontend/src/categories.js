// Categorias de movimentos — rótulos PT-PT e cores. Partilhado entre a página de
// Despesas e o painel para evitar duplicar a definição.
//
// As categorias por omissão são fixas (têm de acompanhar ExpenseController.DEFAULT_KEYS
// no backend). Além destas, cada utilizador pode criar categorias personalizadas: essas
// chegam do backend e são registadas em memória via setCustomCategories(), ficando
// disponíveis em catLabel/catColor tal como as por omissão.
export const DEFAULT_CATEGORY_META = {
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

// Registo (mutável) das categorias personalizadas do utilizador: chave → { label, color }.
let customMeta = {}

/** Substitui o registo de categorias personalizadas (lista vinda de GET /expenses/categories). */
export function setCustomCategories(list) {
  const next = {}
  for (const c of list || []) next[c.key] = { label: c.label, color: c.color }
  customMeta = next
}

export const DEFAULT_CATEGORIES = Object.keys(DEFAULT_CATEGORY_META)

const meta = (c) => customMeta[c] || DEFAULT_CATEGORY_META[c] || DEFAULT_CATEGORY_META.OTHER
export const catLabel = (c) => meta(c).label
export const catColor = (c) => meta(c).color

/** Opções para os seletores de categoria: por omissão + personalizadas do utilizador. */
export function categoryOptions() {
  return [...DEFAULT_CATEGORIES, ...Object.keys(customMeta)].map((c) => ({ value: c, label: catLabel(c) }))
}

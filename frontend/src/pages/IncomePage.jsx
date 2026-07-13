import { useEffect, useState } from 'react'
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer } from 'recharts'
import { api, fmtEur } from '../api'
import Modal, { ConfirmDialog } from '../components/Modal'
import { useToast } from '../components/Toast'
import { IconPencil, IconPlus, IconPie, IconWallet } from '../components/Icons'

const COLORS = ['#6366f1', '#22d3ee', '#10b981', '#f59e0b', '#ef4444', '#a78bfa', '#fb923c', '#e879f9']

const EMPTY_ALLOC = { name: '', mode: 'percentage', value: '' }

function ChartTooltip({ active, payload }) {
  if (!active || !payload?.length) return null
  return (
    <div className="chart-tooltip">
      <div className="tt-label">{payload[0].name}</div>
      <div className="tt-value">{fmtEur(payload[0].value)}</div>
    </div>
  )
}

export default function IncomePage() {
  const toast = useToast()
  const [data, setData] = useState(null)
  const [incomeModal, setIncomeModal] = useState(false)
  const [incomeInput, setIncomeInput] = useState('')
  const [allocModal, setAllocModal] = useState(false)
  const [allocForm, setAllocForm] = useState(EMPTY_ALLOC)
  const [toDelete, setToDelete] = useState(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api.getIncome().then(setData).catch(() => toast.error('Erro', 'Não foi possível carregar os dados.'))
  }, [])

  const saveIncome = async () => {
    setBusy(true)
    try {
      setData(await api.setIncome(Number(incomeInput) || 0))
      setIncomeModal(false)
      toast.success('Rendimento atualizado', `Definido para ${fmtEur(Number(incomeInput) || 0)}.`)
    } catch (e) { toast.error('Erro ao guardar', e.message) }
    finally { setBusy(false) }
  }

  const addAlloc = async () => {
    const value = Number(allocForm.value)
    if (!allocForm.name.trim() || !value || value <= 0) {
      toast.error('Campos em falta', allocForm.mode === 'percentage'
        ? 'Indica o nome da categoria e a percentagem.'
        : 'Indica o nome da categoria e o valor mensal.')
      return
    }
    setBusy(true)
    try {
      const payload = allocForm.mode === 'percentage'
        ? { name: allocForm.name.trim(), percentage: value }
        : { name: allocForm.name.trim(), fixedAmount: value }
      setData(await api.addAllocation(payload))
      setAllocModal(false)
      setAllocForm(EMPTY_ALLOC)
      toast.success('Categoria adicionada', `"${allocForm.name.trim()}" incluída na distribuição.`)
    } catch (e) { toast.error('Erro ao adicionar', e.message) }
    finally { setBusy(false) }
  }

  const removeAlloc = async () => {
    setBusy(true)
    try {
      setData(await api.deleteAllocation(toDelete.id))
      toast.info('Categoria removida', `"${toDelete.name}" já não faz parte da distribuição.`)
      setToDelete(null)
    } catch (e) { toast.error('Erro ao remover', e.message) }
    finally { setBusy(false) }
  }

  if (!data) {
    return (
      <div>
        <div className="skeleton" style={{ height: 96, marginBottom: 18 }} />
        <div className="grid grid-2">
          <div className="skeleton" style={{ height: 320 }} />
          <div className="skeleton" style={{ height: 320 }} />
        </div>
      </div>
    )
  }

  const income = Number(data.monthlyIncome)
  const totalPct = Number(data.totalPercentage)
  const overAllocated = income > 0 && Number(data.unallocated) < 0
  const pieData = data.allocations.map((a) => ({ name: a.name, value: Number(a.amount) }))
  if (Number(data.unallocated) > 0.005) pieData.push({ name: 'Não alocado', value: Number(data.unallocated) })

  const isPct = allocForm.mode === 'percentage'
  const formValue = Number(allocForm.value) || 0
  const formHint = !formValue ? null
    : isPct
      ? (income > 0 ? `≈ ${fmtEur(income * formValue / 100)} por mês` : null)
      : (income > 0 ? `≈ ${(formValue / income * 100).toFixed(1)}% do rendimento` : null)

  return (
    <div>
      <div className="page-head">
        <div>
          <h2>Rendimento</h2>
          <p>Define o teu rendimento mensal e distribui-o por categorias.</p>
        </div>
      </div>

      <div className="card income-hero">
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <span className="stat-icon" style={{ width: 46, height: 46 }}><IconWallet size={22} /></span>
          <div>
            <div className="amount">{fmtEur(income)}</div>
            <div className="caption">Rendimento líquido mensal</div>
          </div>
        </div>
        <button className="btn ghost" onClick={() => { setIncomeInput(income || ''); setIncomeModal(true) }}>
          <IconPencil size={15} /> Editar
        </button>
      </div>

      <div className="grid grid-2">
        <div className="card">
          <div className="card-header">
            <div>
              <h3>Distribuição</h3>
              <div className="sub">Como divides o rendimento todos os meses</div>
            </div>
            <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
              <span className={`badge ${overAllocated ? 'warn' : 'accent'}`}>{totalPct.toFixed(0)}% alocado</span>
              <button className="btn small" onClick={() => setAllocModal(true)}>
                <IconPlus size={14} /> Categoria
              </button>
            </div>
          </div>

          {data.allocations.length === 0 ? (
            <div className="empty-state">
              <div className="empty-icon"><IconPie size={24} /></div>
              <h4>Sem categorias</h4>
              <p>Cria a primeira categoria para começares a distribuir o rendimento — por percentagem (ex: 30% poupança) ou por valor fixo (ex: 400€ renda).</p>
              <button className="btn" onClick={() => setAllocModal(true)}><IconPlus size={15} /> Criar categoria</button>
            </div>
          ) : (
            <div className="table-wrap">
              <table className="responsive">
                <thead>
                  <tr><th>Categoria</th><th>Regra</th><th>%</th><th>Valor</th><th></th></tr>
                </thead>
                <tbody>
                  {data.allocations.map((a, i) => (
                    <tr key={a.id}>
                      <td>
                        <span className="alloc-color" style={{ background: COLORS[i % COLORS.length] }} />
                        <span className="row-title">{a.name}</span>
                      </td>
                      <td data-label="Regra">
                        <span className="type-chip">
                          {a.fixedAmount != null ? 'Valor fixo' : `${Number(a.percentage).toFixed(0)}%`}
                        </span>
                      </td>
                      <td data-label="% do rendimento" className={a.fixedAmount != null ? 'dim' : ''}>
                        {a.effectivePercentage != null ? `${Number(a.effectivePercentage).toFixed(1)}%` : '—'}
                      </td>
                      <td data-label="Valor">{fmtEur(a.amount)}</td>
                      <td className="actions-cell" style={{ textAlign: 'right' }}>
                        <button className="icon-btn danger" onClick={() => setToDelete(a)} aria-label="Remover">✕</button>
                      </td>
                    </tr>
                  ))}
                  <tr>
                    <td className="dim">Não alocado</td>
                    <td></td>
                    <td data-label="% do rendimento" className="dim">{income > 0 ? `${Math.max(0, 100 - totalPct).toFixed(1)}%` : '—'}</td>
                    <td data-label="Valor" className={Number(data.unallocated) < 0 ? 'neg' : 'dim'}>{fmtEur(data.unallocated)}</td>
                    <td></td>
                  </tr>
                </tbody>
              </table>
            </div>
          )}
          {overAllocated && (
            <p className="hint" style={{ color: 'var(--amber)' }}>
              Atenção: a soma das categorias ultrapassa o rendimento mensal em {fmtEur(Math.abs(Number(data.unallocated)))}.
            </p>
          )}
        </div>

        <div className="card">
          <div className="card-header">
            <div>
              <h3>Visão geral</h3>
              <div className="sub">Distribuição do rendimento em euros</div>
            </div>
          </div>
          {pieData.length === 0 || (income === 0 && Number(data.totalAllocated) === 0) ? (
            <div className="empty-state">
              <div className="empty-icon"><IconPie size={24} /></div>
              <h4>Nada para mostrar</h4>
              <p>Define o rendimento mensal e cria categorias para veres o gráfico da distribuição.</p>
            </div>
          ) : (
            <ResponsiveContainer width="100%" height={290}>
              <PieChart>
                <Pie data={pieData} dataKey="value" nameKey="name" innerRadius={62} outerRadius={100}
                     paddingAngle={3} strokeWidth={0}>
                  {pieData.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
                </Pie>
                <Tooltip content={<ChartTooltip />} />
              </PieChart>
            </ResponsiveContainer>
          )}
        </div>
      </div>

      <Modal open={incomeModal} onClose={() => setIncomeModal(false)}
             title="Editar rendimento" subtitle="Valor líquido que recebes por mês." width={420}
             footer={
               <>
                 <button className="btn ghost" onClick={() => setIncomeModal(false)}>Cancelar</button>
                 <button className="btn" onClick={saveIncome} disabled={busy}>{busy ? 'A guardar…' : 'Guardar'}</button>
               </>
             }>
        <div className="field">
          <label>Rendimento mensal</label>
          <div className="input-affix">
            <input type="number" min="0" step="0.01" autoFocus value={incomeInput}
                   onChange={(e) => setIncomeInput(e.target.value)}
                   onKeyDown={(e) => e.key === 'Enter' && saveIncome()} />
            <span className="affix">€</span>
          </div>
        </div>
      </Modal>

      <Modal open={allocModal} onClose={() => setAllocModal(false)}
             title="Nova categoria" subtitle="Reserva uma parte do rendimento por percentagem ou valor fixo." width={440}
             footer={
               <>
                 <button className="btn ghost" onClick={() => setAllocModal(false)}>Cancelar</button>
                 <button className="btn" onClick={addAlloc} disabled={busy}>{busy ? 'A adicionar…' : 'Adicionar'}</button>
               </>
             }>
        <div className="form-grid">
          <div className="field full">
            <label>Nome</label>
            <input placeholder="Ex: Poupança, Renda…" autoFocus value={allocForm.name}
                   onChange={(e) => setAllocForm({ ...allocForm, name: e.target.value })} />
          </div>
          <div className="field full">
            <label>Tipo de regra</label>
            <div className="mode-toggle">
              <button type="button" className={isPct ? 'active' : ''}
                      onClick={() => setAllocForm({ ...allocForm, mode: 'percentage' })}>
                Percentagem
              </button>
              <button type="button" className={!isPct ? 'active' : ''}
                      onClick={() => setAllocForm({ ...allocForm, mode: 'fixed' })}>
                Valor fixo
              </button>
            </div>
            <span className="hint">
              {isPct
                ? 'A categoria acompanha o rendimento — se ele mudar, o valor ajusta-se.'
                : 'A categoria fica sempre com o mesmo valor em euros, independentemente do rendimento.'}
            </span>
          </div>
          <div className="field full">
            <label>{isPct ? 'Percentagem do rendimento' : 'Valor mensal'}</label>
            <div className="input-affix">
              <input type="number" min="0" max={isPct ? 100 : undefined} step={isPct ? 0.5 : 0.01}
                     placeholder={isPct ? 'Ex: 30' : 'Ex: 400'} value={allocForm.value}
                     onChange={(e) => setAllocForm({ ...allocForm, value: e.target.value })}
                     onKeyDown={(e) => e.key === 'Enter' && addAlloc()} />
              <span className="affix">{isPct ? '%' : '€'}</span>
            </div>
            {formHint && <span className="hint">{formHint}</span>}
          </div>
        </div>
      </Modal>

      <ConfirmDialog open={!!toDelete} busy={busy}
                     title="Remover categoria?"
                     message={`A categoria "${toDelete?.name}" vai ser removida da distribuição. Esta ação não pode ser anulada.`}
                     confirmLabel="Remover"
                     onConfirm={removeAlloc} onCancel={() => setToDelete(null)} />
    </div>
  )
}

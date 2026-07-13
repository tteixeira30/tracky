import { useEffect, useState } from 'react'
import { api, fmtEur } from '../api'
import Modal, { ConfirmDialog } from '../components/Modal'
import { useToast } from '../components/Toast'
import { IconCalendar, IconCheck, IconPlus, IconRefresh, IconTarget } from '../components/Icons'

const EMPTY_FORM = { name: '', targetAmount: '', monthlyAllocation: '', savedAmount: '', autoDeposit: false }

export default function GoalsPage() {
  const toast = useToast()
  const [goals, setGoals] = useState(null)
  const [addModal, setAddModal] = useState(false)
  const [form, setForm] = useState(EMPTY_FORM)
  const [contrib, setContrib] = useState({})
  const [toDelete, setToDelete] = useState(null)
  const [busy, setBusy] = useState(false)

  const load = () => api.getGoals().then(setGoals)

  useEffect(() => {
    load().catch(() => toast.error('Erro', 'Não foi possível carregar os objetivos.'))
  }, [])

  const add = async () => {
    if (!form.name.trim() || !form.targetAmount || !form.monthlyAllocation) {
      toast.error('Campos em falta', 'Indica o nome, o valor do objetivo e a alocação mensal.')
      return
    }
    setBusy(true)
    try {
      await api.addGoal({
        name: form.name.trim(),
        targetAmount: Number(form.targetAmount),
        monthlyAllocation: Number(form.monthlyAllocation),
        savedAmount: Number(form.savedAmount) || 0,
        autoDeposit: form.autoDeposit,
      })
      setAddModal(false)
      setForm(EMPTY_FORM)
      await load()
      toast.success('Objetivo criado', `"${form.name.trim()}" adicionado aos teus objetivos.`)
    } catch (e) { toast.error('Erro ao criar', e.message) }
    finally { setBusy(false) }
  }

  const contribute = async (goal) => {
    const amount = Number(contrib[goal.id])
    if (!amount) {
      toast.error('Valor em falta', 'Indica o valor da contribuição.')
      return
    }
    try {
      const updated = await api.contributeGoal(goal.id, amount)
      setContrib({ ...contrib, [goal.id]: '' })
      await load()
      if (Number(updated.progressPercent) >= 100) {
        toast.success('Objetivo atingido! 🎉', `Parabéns — completaste "${goal.name}".`)
      } else {
        toast.success('Contribuição registada', `${fmtEur(amount)} adicionados a "${goal.name}".`)
      }
    } catch (e) { toast.error('Erro ao contribuir', e.message) }
  }

  const remove = async () => {
    setBusy(true)
    try {
      await api.deleteGoal(toDelete.id)
      await load()
      toast.info('Objetivo removido', `"${toDelete.name}" foi eliminado.`)
      setToDelete(null)
    } catch (e) { toast.error('Erro ao remover', e.message) }
    finally { setBusy(false) }
  }

  const simulateDeposits = async () => {
    try {
      const result = await api.applyDeposits('goals')
      if (result.applied.length === 0) {
        toast.info('Sem depósitos automáticos', 'Nenhum objetivo tem o depósito mensal automático ativo.')
        return
      }
      await load()
      const names = result.applied.map((a) => a.name).join(', ')
      toast.success('Depósitos aplicados', `${fmtEur(result.totalAmount)} em: ${names}.`)
    } catch (e) { toast.error('Erro ao aplicar depósitos', e.message) }
  }

  if (!goals) {
    return (
      <div className="goals-grid">
        {[0, 1].map((i) => <div key={i} className="skeleton" style={{ height: 210 }} />)}
      </div>
    )
  }

  const estimateMonths = () => {
    const target = Number(form.targetAmount) || 0
    const monthly = Number(form.monthlyAllocation) || 0
    const saved = Number(form.savedAmount) || 0
    if (target <= saved) return 'Objetivo já atingido com o valor poupado.'
    if (monthly <= 0) return null
    const months = Math.ceil((target - saved) / monthly)
    const date = new Date()
    date.setMonth(date.getMonth() + months)
    return `≈ ${months} ${months === 1 ? 'mês' : 'meses'} — ${date.toLocaleDateString('pt-PT', { month: 'long', year: 'numeric' })}`
  }

  return (
    <div>
      <div className="page-head">
        <div>
          <h2>Objetivos</h2>
          <p>Define metas de poupança e acompanha o teu progresso.</p>
        </div>
        <div className="page-actions">
          <button className="btn ghost" onClick={simulateDeposits} title="Aplica já a alocação mensal dos objetivos com depósito automático">
            <IconRefresh size={15} /> Simular depósito mensal
          </button>
          <button className="btn" onClick={() => setAddModal(true)}><IconPlus size={15} /> Novo objetivo</button>
        </div>
      </div>

      {goals.length === 0 ? (
        <div className="card">
          <div className="empty-state">
            <div className="empty-icon"><IconTarget size={24} /></div>
            <h4>Ainda sem objetivos</h4>
            <p>Cria o teu primeiro objetivo — um fundo de emergência, uma viagem, a entrada para casa — e acompanha quanto falta.</p>
            <button className="btn" onClick={() => setAddModal(true)}><IconPlus size={15} /> Criar objetivo</button>
          </div>
        </div>
      ) : (
        <div className="goals-grid">
          {goals.map((g) => {
            const done = Number(g.progressPercent) >= 100
            return (
              <div className="card goal-card" key={g.id}>
                <div className="goal-top">
                  <div className="goal-title">
                    <span className={`goal-emoji ${done ? 'done' : ''}`}>
                      {done ? <IconCheck size={19} /> : <IconTarget size={19} />}
                    </span>
                    {g.name}
                  </div>
                  <button className="icon-btn danger" onClick={() => setToDelete(g)} aria-label="Eliminar">✕</button>
                </div>

                <div>
                  <div className="goal-amounts">
                    <span className="big">{fmtEur(g.savedAmount)}</span>
                    <span className="of">de {fmtEur(g.targetAmount)} · {Number(g.progressPercent).toFixed(1)}%</span>
                  </div>
                  <div className="progress-track" style={{ marginTop: 9 }}>
                    <div className={`progress-fill ${done ? 'done' : ''}`}
                         style={{ width: `${Math.min(100, g.progressPercent)}%` }} />
                  </div>
                </div>

                <div className="goal-meta">
                  <span className="badge accent">{fmtEur(g.monthlyAllocation)}/mês</span>
                  {g.autoDeposit && <span className="badge live">Auto · dia 1</span>}
                  {done ? (
                    <span className="badge live"><IconCheck size={12} /> Atingido</span>
                  ) : (
                    <>
                      {g.monthsRemaining != null && (
                        <span className="badge">{g.monthsRemaining} {g.monthsRemaining === 1 ? 'mês' : 'meses'} restantes</span>
                      )}
                      {g.estimatedDate && (
                        <span className="badge"><IconCalendar size={12} /> {new Date(g.estimatedDate).toLocaleDateString('pt-PT', { month: 'short', year: 'numeric' })}</span>
                      )}
                    </>
                  )}
                </div>

                {!done && (
                  <div className="goal-contribute">
                    <div className="input-affix">
                      <input type="number" step="0.01" placeholder="Valor"
                             value={contrib[g.id] ?? ''}
                             onChange={(e) => setContrib({ ...contrib, [g.id]: e.target.value })}
                             onKeyDown={(e) => e.key === 'Enter' && contribute(g)} />
                      <span className="affix">€</span>
                    </div>
                    <button className="btn small ghost" onClick={() => contribute(g)}>
                      <IconPlus size={13} /> Contribuir
                    </button>
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      <Modal open={addModal} onClose={() => setAddModal(false)}
             title="Novo objetivo" subtitle="Define a meta e quanto consegues alocar por mês."
             footer={
               <>
                 <button className="btn ghost" onClick={() => setAddModal(false)}>Cancelar</button>
                 <button className="btn" onClick={add} disabled={busy}>{busy ? 'A criar…' : 'Criar objetivo'}</button>
               </>
             }>
        <div className="form-grid">
          <div className="field full">
            <label>Nome</label>
            <input placeholder="Ex: Fundo de emergência" autoFocus value={form.name}
                   onChange={(e) => setForm({ ...form, name: e.target.value })} />
          </div>
          <div className="field">
            <label>Valor do objetivo</label>
            <div className="input-affix">
              <input type="number" min="0" step="0.01" placeholder="Ex: 10000" value={form.targetAmount}
                     onChange={(e) => setForm({ ...form, targetAmount: e.target.value })} />
              <span className="affix">€</span>
            </div>
          </div>
          <div className="field">
            <label>Alocação mensal</label>
            <div className="input-affix">
              <input type="number" min="0" step="0.01" placeholder="Ex: 300" value={form.monthlyAllocation}
                     onChange={(e) => setForm({ ...form, monthlyAllocation: e.target.value })} />
              <span className="affix">€</span>
            </div>
          </div>
          <div className="field full">
            <label>Já poupado <span className="dim">(opcional)</span></label>
            <div className="input-affix">
              <input type="number" min="0" step="0.01" placeholder="0" value={form.savedAmount}
                     onChange={(e) => setForm({ ...form, savedAmount: e.target.value })} />
              <span className="affix">€</span>
            </div>
            {estimateMonths() && <span className="hint">{estimateMonths()}</span>}
          </div>
          <div className="field full">
            <label className="check-row">
              <input type="checkbox" checked={form.autoDeposit}
                     onChange={(e) => setForm({ ...form, autoDeposit: e.target.checked })} />
              <span>Depósito automático mensal</span>
            </label>
            <span className="hint">
              A alocação mensal é adicionada automaticamente no dia 1 de cada mês (com a app ligada, ou no arranque seguinte).
              Também podes usar o botão "Simular depósito mensal".
            </span>
          </div>
        </div>
      </Modal>

      <ConfirmDialog open={!!toDelete} busy={busy}
                     title="Eliminar objetivo?"
                     message={`"${toDelete?.name}" e o progresso registado vão ser eliminados. Esta ação não pode ser anulada.`}
                     onConfirm={remove} onCancel={() => setToDelete(null)} />
    </div>
  )
}

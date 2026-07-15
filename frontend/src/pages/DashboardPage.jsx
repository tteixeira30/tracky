import { useEffect, useState } from 'react'
import { AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from 'recharts'
import { api, fmtEur, fmtPct, fmtMoneyShort } from '../api'
import { useAuth } from '../components/AuthContext'
import { useChartColors } from '../components/ThemeContext'
import {
  IconWallet, IconTrendingUp, IconTarget, IconCheck, IconInfo,
  IconSparkle, IconCoins, IconActivity,
} from '../components/Icons'

const INSIGHT_ICONS = {
  trending: IconTrendingUp,
  target: IconTarget,
  check: IconCheck,
  wallet: IconWallet,
}

const ACTIVITY_ICONS = {
  investment: IconTrendingUp,
  goal: IconTarget,
}

function ChartTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null
  return (
    <div className="chart-tooltip">
      <div className="tt-label">{new Date(label).toLocaleDateString('pt-PT', { day: 'numeric', month: 'long', year: 'numeric' })}</div>
      <div className="tt-value">{fmtEur(payload[0].value)}</div>
    </div>
  )
}

function fmtMonth(m) {
  if (!m) return ''
  const [y, mo] = m.split('-').map(Number)
  const label = new Date(y, mo - 1, 1).toLocaleDateString('pt-PT', { month: 'long', year: 'numeric' })
  return label.charAt(0).toUpperCase() + label.slice(1)
}

function timeAgo(iso) {
  const then = new Date(iso).getTime()
  const days = Math.floor((Date.now() - then) / 86400000)
  if (days <= 0) return 'hoje'
  if (days === 1) return 'ontem'
  if (days < 30) return `há ${days} dias`
  const months = Math.floor(days / 30)
  if (months === 1) return 'há 1 mês'
  if (months < 12) return `há ${months} meses`
  const years = Math.floor(months / 12)
  return years === 1 ? 'há 1 ano' : `há ${years} anos`
}

export default function DashboardPage() {
  const { user } = useAuth()
  const chart = useChartColors()
  const [data, setData] = useState(null)
  const [error, setError] = useState(false)

  useEffect(() => {
    api.getDashboard().then(setData).catch(() => setError(true))
  }, [])

  if (error) {
    return (
      <div className="card">
        <div className="empty-state">
          <div className="empty-icon"><IconInfo size={24} /></div>
          <h4>Não foi possível carregar o painel</h4>
          <p>Tenta recarregar a página.</p>
        </div>
      </div>
    )
  }

  if (!data) {
    return (
      <div>
        <div className="kpi-grid">
          {[0, 1, 2, 3].map((i) => <div key={i} className="skeleton" style={{ height: 118 }} />)}
        </div>
        <div className="skeleton" style={{ height: 300, marginTop: 20, borderRadius: 16 }} />
      </div>
    )
  }

  const firstName = user?.name?.split(' ')[0] || ''
  const gainPositive = Number(data.investmentGainPercent) >= 0
  const evolution = (data.evolution || []).map((p) => ({ ...p, value: Number(p.value) }))

  const kpis = [
    {
      key: 'net', label: 'Património líquido', icon: IconCoins,
      value: fmtEur(data.netWorth), tone: 'accent',
      sub: 'Investimentos + poupança em objetivos',
    },
    {
      key: 'income', label: data.incomeMonth ? `Rendimento · ${fmtMonth(data.incomeMonth)}` : 'Rendimento do mês', icon: IconWallet,
      value: fmtEur(data.monthlyIncome),
      sub: Number(data.unallocated) > 0 ? `${fmtEur(data.unallocated)} por alocar` : 'Totalmente distribuído',
    },
    {
      key: 'invested', label: 'Valor investido', icon: IconTrendingUp,
      value: fmtEur(data.totalInvested),
      sub: <span className={gainPositive ? 'pos' : 'neg'}>{fmtPct(data.investmentGainPercent)} · {fmtEur(data.investmentGain)}</span>,
    },
    {
      key: 'goals', label: 'Objetivos', icon: IconTarget,
      value: `${data.goalsProgressPercent}%`,
      sub: `${data.goalsCompleted}/${data.goalsCount} concluídos · ${fmtEur(data.totalSaved)} poupados`,
    },
  ]

  return (
    <div>
      <div className="page-head">
        <div>
          <h2>Olá{firstName ? `, ${firstName}` : ''}</h2>
          <p>Aqui está o resumo das tuas finanças.</p>
        </div>
      </div>

      <div className="kpi-grid">
        {kpis.map((k) => (
          <div className={`card kpi-card ${k.tone || ''}`} key={k.key}>
            <div className="kpi-top">
              <span className="kpi-icon"><k.icon size={17} /></span>
              <span className="kpi-label">{k.label}</span>
            </div>
            <div className="kpi-value">{k.value}</div>
            <div className="kpi-sub">{k.sub}</div>
          </div>
        ))}
      </div>

      <div className="dash-grid">
        <div className="card dash-chart">
          <div className="card-header">
            <div>
              <h3>Evolução do património</h3>
              <div className="sub">Últimos 6 meses · portefólio + poupança</div>
            </div>
          </div>
          {evolution.length === 0 ? (
            <div className="empty-state">
              <div className="empty-icon"><IconTrendingUp size={24} /></div>
              <h4>Sem histórico ainda</h4>
              <p>Adiciona investimentos com símbolo (ex: VWCE.DE, BTC) para veres a evolução ao longo do tempo.</p>
            </div>
          ) : (
            <ResponsiveContainer width="100%" height={260}>
              <AreaChart data={evolution} margin={{ top: 8, right: 4, left: 0, bottom: 0 }}>
                <defs>
                  <linearGradient id="dash-grad" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#22d3ee" stopOpacity={0.32} />
                    <stop offset="100%" stopColor="#22d3ee" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid stroke={chart.grid} strokeDasharray="3 3" vertical={false} />
                <XAxis dataKey="date" stroke={chart.axis} fontSize={11.5} tickMargin={10} axisLine={false} tickLine={false}
                       tickFormatter={(d) => new Date(d).toLocaleDateString('pt-PT', { day: '2-digit', month: 'short' })} />
                <YAxis stroke={chart.axis} fontSize={11.5} axisLine={false} tickLine={false} width={72}
                       tickFormatter={fmtMoneyShort} domain={['auto', 'auto']} />
                <Tooltip content={<ChartTooltip />} />
                <Area type="monotone" dataKey="value" stroke="#22d3ee" strokeWidth={2.2} fill="url(#dash-grad)"
                      activeDot={{ r: 4, strokeWidth: 0 }} />
              </AreaChart>
            </ResponsiveContainer>
          )}
        </div>

        <div className="card dash-side">
          <div className="card-header">
            <div><h3><IconSparkle size={16} /> Insights</h3></div>
          </div>
          {data.insights.length === 0 ? (
            <p className="dim" style={{ padding: '4px 2px' }}>Sem destaques por agora — continua a registar os teus dados.</p>
          ) : (
            <ul className="insight-list">
              {data.insights.map((ins, i) => {
                const Icon = INSIGHT_ICONS[ins.icon] || IconInfo
                return (
                  <li key={i} className={`insight ${ins.kind}`}>
                    <span className="insight-icon"><Icon size={15} /></span>
                    <div>
                      <strong>{ins.title}</strong>
                      <span>{ins.detail}</span>
                    </div>
                  </li>
                )
              })}
            </ul>
          )}
        </div>
      </div>

      <div className="card dash-activity">
        <div className="card-header">
          <div><h3><IconActivity size={16} /> Atividade recente</h3></div>
        </div>
        {data.recentActivity.length === 0 ? (
          <p className="dim" style={{ padding: '4px 2px' }}>Ainda sem atividade registada.</p>
        ) : (
          <ul className="activity-list">
            {data.recentActivity.map((a, i) => {
              const Icon = ACTIVITY_ICONS[a.type] || IconInfo
              return (
                <li key={i} className="activity">
                  <span className={`activity-icon ${a.type}`}><Icon size={15} /></span>
                  <div className="activity-main">
                    <strong>{a.title}</strong>
                    <span>{a.subtitle}</span>
                  </div>
                  <span className="activity-time">{timeAgo(a.at)}</span>
                </li>
              )
            })}
          </ul>
        )}
      </div>
    </div>
  )
}

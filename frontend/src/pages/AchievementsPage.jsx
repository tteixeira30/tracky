import { useEffect, useState } from 'react'
import { api, fmtEur } from '../api'
import {
  IconTrophy, IconCoins, IconTrendingUp, IconTarget, IconRepeat, IconSparkle,
  IconCheck, IconWallet, IconCalendar, IconFlame, IconStar, IconLock, IconInfo,
} from '../components/Icons'

const ICONS = {
  trophy: IconTrophy, coins: IconCoins, trending: IconTrendingUp, target: IconTarget,
  repeat: IconRepeat, sparkle: IconSparkle, check: IconCheck, wallet: IconWallet,
  calendar: IconCalendar, flame: IconFlame, star: IconStar,
}
const CATEGORY_ORDER = ['Investimento', 'Poupança', 'Consistência', 'Objetivos', 'Rentabilidade', 'Planeamento']

function fmtValue(v, unit) {
  if (v == null) return ''
  if (unit === 'eur') return fmtEur(v)
  if (unit === 'pct') return `${Math.round(v)}%`
  return String(Math.round(v))
}

export default function AchievementsPage() {
  const [data, setData] = useState(null)
  const [error, setError] = useState(false)

  useEffect(() => {
    api.getAchievements().then(setData).catch(() => setError(true))
  }, [])

  if (error) {
    return (
      <div className="card">
        <div className="empty-state">
          <div className="empty-icon"><IconInfo size={24} /></div>
          <h4>Não foi possível carregar as conquistas</h4>
          <p>Tenta recarregar a página.</p>
        </div>
      </div>
    )
  }

  if (!data) {
    return (
      <div>
        <div className="skeleton" style={{ height: 150, borderRadius: 16, marginBottom: 20 }} />
        <div className="ach-grid">
          {[0, 1, 2, 3, 4, 5].map((i) => <div key={i} className="skeleton" style={{ height: 120 }} />)}
        </div>
      </div>
    )
  }

  const byCategory = CATEGORY_ORDER
    .map((cat) => ({ cat, items: data.achievements.filter((a) => a.category === cat) }))
    .filter((g) => g.items.length > 0)

  return (
    <div>
      <div className="page-head">
        <div>
          <h2>Conquistas</h2>
          <p>Sobe de nível à medida que constróis o teu futuro financeiro.</p>
        </div>
      </div>

      <div className="card level-card">
        <div className="level-badge">
          <IconTrophy size={26} />
          <span className="level-num">{data.level}</span>
        </div>
        <div className="level-info">
          <div className="level-top">
            <h3>Nível {data.level} · {data.levelName}</h3>
            <span className="level-points">{data.points} pontos</span>
          </div>
          <div className="progress-track" style={{ marginTop: 10 }}>
            <div className="progress-fill" style={{ width: `${Math.round(data.pointsIntoLevel / data.pointsForNextLevel * 100)}%` }} />
          </div>
          <div className="level-foot">
            <span>{data.level >= 8 ? 'Nível máximo atingido!' : `${data.pointsForNextLevel - data.pointsIntoLevel} pontos para o nível ${data.level + 1}`}</span>
            <span>{data.unlocked}/{data.total} desbloqueadas · {data.percentUnlocked}%</span>
          </div>
        </div>
      </div>

      {byCategory.map((group) => (
        <div key={group.cat} className="ach-section">
          <h3 className="ach-cat">{group.cat}</h3>
          <div className="ach-grid">
            {group.items.map((a) => {
              const Icon = ICONS[a.icon] || IconTrophy
              return (
                <div key={a.id} className={`ach-card ${a.unlocked ? 'unlocked' : 'locked'}`}>
                  <div className="ach-top">
                    <span className="ach-icon">{a.unlocked ? <Icon size={20} /> : <IconLock size={18} />}</span>
                    <span className="ach-points">{a.points} pt</span>
                  </div>
                  <div className="ach-body">
                    <strong>{a.title}</strong>
                    <span>{a.description}</span>
                  </div>
                  {a.unlocked ? (
                    <div className="ach-done"><IconCheck size={13} /> Desbloqueada</div>
                  ) : a.unit !== 'bool' ? (
                    <div className="ach-progress">
                      <div className="progress-track">
                        <div className="progress-fill" style={{ width: `${a.progress}%` }} />
                      </div>
                      <span>{fmtValue(a.current, a.unit)} / {fmtValue(a.target, a.unit)}</span>
                    </div>
                  ) : (
                    <div className="ach-progress"><span className="dim">Por desbloquear</span></div>
                  )}
                </div>
              )
            })}
          </div>
        </div>
      ))}
    </div>
  )
}

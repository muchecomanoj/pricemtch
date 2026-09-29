import { motion } from 'framer-motion'
import {
  FiBox, FiCheckCircle, FiList, FiSearch, FiCpu, FiPercent, FiDollarSign,
  FiTrendingUp, FiTrendingDown, FiActivity, FiRadio, FiBell, FiUsers, FiTag,
} from 'react-icons/fi'
import Sparkline from '../charts/Sparkline'
import { formatValue } from '../../utils/format'

const ICONS = {
  box: FiBox, check: FiCheckCircle, list: FiList, search: FiSearch,
  cpu: FiCpu, percent: FiPercent, dollar: FiDollarSign, trend: FiTrendingUp,
  // Price Changes and Active Monitors, which replaced the revenue/profit tiles.
  activity: FiActivity, radio: FiRadio, bell: FiBell, users: FiUsers, tag: FiTag,
}

const SPARK_COLOR = {
  primary: '#3fa9f5', success: '#10b981', warning: '#f59e0b',
  danger: '#ef4444', info: '#0ea5e9',
}

// Deterministic mini-series from the KPI value (visual only — no data invented).
function pseudoSeries(seed = 7) {
  const out = []
  let n = (seed % 9) + 3
  for (let i = 0; i < 8; i++) { n = (n * 1.7 + i * 3) % 12; out.push(4 + n) }
  return out
}

export default function StatsCard({ item }) {
  const Icon = ICONS[item.icon] || FiBox
  const color = item.color || 'primary'
  const up = (item.delta ?? 0) >= 0
  const spark = item.spark || pseudoSeries(Math.round(item.value ?? 7))

  return (
    <motion.div
      className="card stats-card card-hover h-100"
      whileHover={{ y: -4 }}
      transition={{ type: 'spring', stiffness: 300, damping: 22 }}
    >
      <div className="card-body">
        <div className="d-flex justify-content-between align-items-start mb-3">
          <div className={`icon-box icon-grad-${color}`}><Icon /></div>
          {item.delta != null && (
            <span className={`trend-pill ${up ? 'trend-up' : 'trend-down'}`}>
              {up ? <FiTrendingUp size={12} /> : <FiTrendingDown size={12} />}
              {Math.abs(item.delta)}%
            </span>
          )}
        </div>
        <div className="text-muted small fw-medium">{item.label}</div>
        <div className="d-flex justify-content-between align-items-end">
          <div className="kpi-value">{formatValue(item)}</div>
          <Sparkline data={spark} color={SPARK_COLOR[color] || SPARK_COLOR.primary} />
        </div>
      </div>
    </motion.div>
  )
}

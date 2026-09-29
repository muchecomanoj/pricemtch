import { FiCheck, FiCircle } from 'react-icons/fi'

// Password rules — exported so forms can validate against the same source.
export const PW_RULES = [
  { key: 'len', label: 'At least 8 characters', test: (v) => v.length >= 8 },
  { key: 'upper', label: 'One uppercase letter', test: (v) => /[A-Z]/.test(v) },
  { key: 'lower', label: 'One lowercase letter', test: (v) => /[a-z]/.test(v) },
  { key: 'num', label: 'One number', test: (v) => /\d/.test(v) },
  { key: 'special', label: 'One special character', test: (v) => /[^A-Za-z0-9]/.test(v) },
]

export const isStrongPassword = (v = '') => PW_RULES.every((r) => r.test(v))

const LEVELS = [
  { label: 'Very weak', color: '#ef4444' },
  { label: 'Weak', color: '#f59e0b' },
  { label: 'Fair', color: '#f59e0b' },
  { label: 'Good', color: '#22c55e' },
  { label: 'Strong', color: '#16a34a' },
]

// Strength meter + live requirements checklist.
export default function PasswordStrength({ value = '' }) {
  const passed = PW_RULES.filter((r) => r.test(value)).length
  const pct = (passed / PW_RULES.length) * 100
  const level = LEVELS[Math.max(0, passed - 1)] || LEVELS[0]

  return (
    <div>
      <div className="d-flex justify-content-between align-items-center mb-1">
        <span className="small text-muted">Password strength</span>
        {value && <span className="small fw-semibold" style={{ color: level.color }}>{level.label}</span>}
      </div>
      <div className="pw-meter mb-3">
        <span style={{ width: `${value ? pct : 0}%`, background: level.color }} />
      </div>

      <div className="row g-1">
        {PW_RULES.map((r) => {
          const ok = r.test(value)
          return (
            <div className="col-12 col-sm-6" key={r.key}>
              <div className={`pw-req ${ok ? 'ok' : ''}`}>
                {ok ? <FiCheck size={13} strokeWidth={3} /> : <FiCircle size={9} />}
                {r.label}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}

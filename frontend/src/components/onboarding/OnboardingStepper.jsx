import { motion } from 'framer-motion'
import { FiCheck } from 'react-icons/fi'

// Vertical onboarding timeline. Completed steps show a green check; the current
// step glows. Collapses to a compact progress bar on mobile.
// `completed` marks the whole flow as finished, so the final (current) step is
// shown green with a check rather than the blue "in progress" dot.
export default function OnboardingStepper({ steps, currentIndex, completed = false, title = 'Get started', subtitle, footer }) {
  const pct = Math.round((currentIndex / Math.max(1, steps.length - 1)) * 100)

  return (
    <aside className="ob-rail">
      <div className="position-relative">
        <div className="d-flex align-items-center gap-2 mb-4">
          <div className="d-grid rounded-3 text-white fw-bold"
            style={{ width: 38, height: 38, placeItems: 'center', background: 'var(--grad-primary)' }}>PI</div>
          <div>
            <div className="fw-bold text-white" style={{ fontFamily: 'Plus Jakarta Sans' }}>{title}</div>
            {subtitle && <div style={{ fontSize: 11, color: '#64748b' }}>{subtitle}</div>}
          </div>
        </div>

        {/* Mobile progress bar */}
        <div className="d-lg-none mb-2">
          <div className="d-flex justify-content-between small mb-1" style={{ color: '#94a3b8' }}>
            <span>{steps[currentIndex]?.label}</span>
            <span>{currentIndex + 1}/{steps.length}</span>
          </div>
          <div className="progress" style={{ height: 6, background: 'rgba(255,255,255,0.1)' }}>
            <motion.div className="progress-bar" initial={{ width: 0 }} animate={{ width: `${pct}%` }}
              transition={{ duration: 0.5 }} style={{ background: 'var(--grad-primary)' }} />
          </div>
        </div>

        {/* Desktop timeline */}
        <div className="ob-rail-steps d-none d-lg-block">
          {steps.map((s, i) => {
            const done = i < currentIndex || (completed && i === currentIndex)
            const current = i === currentIndex && !completed
            return (
              <div key={s.key} className={`ob-step ${done ? 'done' : ''} ${current ? 'current' : ''}`}>
                <div className="ob-dot">
                  {done ? <FiCheck size={14} strokeWidth={3} /> : i + 1}
                </div>
                <div>
                  <div className="ob-step-label">{s.label}</div>
                  {s.desc && <div className="ob-step-desc">{s.desc}</div>}
                </div>
              </div>
            )
          })}
        </div>

        {footer && <div className="ob-rail-steps d-none d-lg-block mt-4">{footer}</div>}
      </div>
    </aside>
  )
}

import { motion } from 'framer-motion'
import { FiCheck } from 'react-icons/fi'

// Compact horizontal stepper for short wizards.
// `inline` renders it as an in-page card (inside the app shell, where the
// sidebar/navbar already provide branding); otherwise it's a sticky top bar.
export default function StepBar({ steps, currentIndex, title, subtitle, onExit, inline }) {
  const pct = (currentIndex / Math.max(1, steps.length - 1)) * 100

  return (
    <header className={inline ? 'ob-stepbar-inline' : 'ob-topbar'}>
      <div className={inline ? 'd-flex align-items-center gap-3' : 'ob-topbar-inner'}>
        {/* Brand + title — only when standalone; the app shell already brands it. */}
        {!inline && (
          <div className="ob-brand">
            <span className="ob-brand-mark">PI</span>
            <span className="d-none d-md-block">
              <span className="ob-brand-title d-block">{title}</span>
              {subtitle && <span className="ob-brand-sub d-block">{subtitle}</span>}
            </span>
          </div>
        )}

        {/* Steps */}
        <div className="ob-topbar-steps">
          {steps.map((s, i) => {
            const done = i < currentIndex
            const current = i === currentIndex
            return (
              <div key={s.key} className={`ob-hstep ${done ? 'done' : ''} ${current ? 'current' : ''}`}>
                <span className="ob-hdot">{done ? <FiCheck size={12} strokeWidth={3} /> : i + 1}</span>
                <span className="ob-hlabel d-none d-lg-inline">{s.label}</span>
                {i < steps.length - 1 && <span className="ob-hline" />}
              </div>
            )
          })}
        </div>

        {onExit && !inline && (
          <button type="button" className="ob-exit" onClick={onExit}>Exit</button>
        )}
      </div>

      {/* Mobile progress */}
      <div className={`d-lg-none ${inline ? 'pt-2' : 'px-3 pb-2'}`}>
        <div className="d-flex justify-content-between small text-muted mb-1">
          <span>{steps[currentIndex]?.label}</span>
          <span>{currentIndex + 1}/{steps.length}</span>
        </div>
        <div className="progress" style={{ height: 4 }}>
          <motion.div className="progress-bar" initial={{ width: 0 }} animate={{ width: `${pct}%` }}
            transition={{ duration: 0.4 }} style={{ background: 'var(--grad-primary)' }} />
        </div>
      </div>
    </header>
  )
}

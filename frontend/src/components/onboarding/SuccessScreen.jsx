import { motion } from 'framer-motion'
import { FiCheck } from 'react-icons/fi'

const COLORS = ['#3fa9f5', '#22c55e', '#f59e0b', '#ef4444', '#8b5cf6', '#0ea5e9']

// Deterministic confetti spread (no randomness needed for a pleasing result).
const PIECES = Array.from({ length: 28 }, (_, i) => ({
  id: i,
  x: (i % 14) * 7.5 - 48,       // spread across
  delay: (i % 7) * 0.06,
  rotate: (i * 47) % 360,
  color: COLORS[i % COLORS.length],
  drift: ((i * 13) % 60) - 30,
}))

// Celebration screen: animated checkmark + confetti burst.
export default function SuccessScreen({ title, message, children, confetti = true }) {
  return (
    <div className="text-center position-relative" style={{ overflow: 'hidden' }}>
      {confetti && (
        <div className="position-absolute w-100" style={{ top: 0, left: 0, height: 320, pointerEvents: 'none' }}>
          {PIECES.map((p) => (
            <motion.span
              key={p.id}
              className="confetti-piece"
              style={{ background: p.color, left: `calc(50% + ${p.x}px)`, top: 20 }}
              initial={{ opacity: 0, y: -10, rotate: 0 }}
              animate={{ opacity: [0, 1, 1, 0], y: 300, x: p.drift, rotate: p.rotate + 260 }}
              transition={{ duration: 2.2, delay: p.delay, ease: 'easeOut' }}
            />
          ))}
        </div>
      )}

      <motion.div
        className="success-ring mb-3"
        initial={{ scale: 0, rotate: -90 }}
        animate={{ scale: 1, rotate: 0 }}
        transition={{ type: 'spring', stiffness: 220, damping: 14, delay: 0.1 }}
      >
        <motion.span
          initial={{ pathLength: 0, opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.35 }}
        >
          <FiCheck size={44} strokeWidth={3} />
        </motion.span>
      </motion.div>

      <motion.h4 className="fw-bold mb-2"
        initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.3 }}>
        {title}
      </motion.h4>
      {message && (
        <motion.p className="text-muted"
          initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.4 }}>
          {message}
        </motion.p>
      )}
      <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.5 }}>
        {children}
      </motion.div>
    </div>
  )
}

import { motion } from 'framer-motion'
import { FiCheckCircle } from 'react-icons/fi'

// Generic selectable card — used for billing cycles and payment methods.
export default function OptionCard({ selected, onSelect, icon: Icon, title, subtitle, right, badge, children }) {
  return (
    <motion.button
      type="button"
      className={`opt-card ${selected ? 'selected' : ''}`}
      onClick={onSelect}
      whileTap={{ scale: 0.985 }}
      whileHover={{ y: -2 }}
      transition={{ type: 'spring', stiffness: 320, damping: 24 }}
    >
      {badge && (
        <span className="badge text-bg-success position-absolute" style={{ top: -9, right: 12, fontSize: '0.66rem' }}>
          {badge}
        </span>
      )}
      <div className="d-flex align-items-center gap-3">
        {Icon && (
          <span className="d-grid rounded-3 flex-shrink-0"
            style={{ width: 40, height: 40, placeItems: 'center', background: 'var(--hover-soft)', color: 'var(--primary)' }}>
            <Icon size={18} />
          </span>
        )}
        <div className="flex-grow-1">
          <div className="fw-semibold d-flex align-items-center gap-2">
            {title}
            {selected && <FiCheckCircle className="text-primary" size={15} />}
          </div>
          {subtitle && <div className="text-muted small">{subtitle}</div>}
          {children}
        </div>
        {right && <div className="text-end flex-shrink-0">{right}</div>}
      </div>
    </motion.button>
  )
}

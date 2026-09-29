import { FiInbox } from 'react-icons/fi'

export default function EmptyState({ icon: Icon = FiInbox, title = 'Nothing here yet', message, action }) {
  return (
    <div className="text-center py-5 text-muted">
      <Icon size={42} className="mb-3 opacity-50" />
      <h6 className="fw-semibold">{title}</h6>
      {message && <p className="small mb-3">{message}</p>}
      {action}
    </div>
  )
}

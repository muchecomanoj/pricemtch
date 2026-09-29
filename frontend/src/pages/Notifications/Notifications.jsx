import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { FiBell, FiCheck } from 'react-icons/fi'
import PageHeader from '../../components/common/PageHeader'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import Loader from '../../components/common/Loader'
import EmptyState from '../../components/common/EmptyState'
import Pagination from '../../components/common/Pagination'
import { notificationService } from '../../services/notificationService'
import { useNotification } from '../../context/NotificationContext'
import { styleFor, resolveLink } from '../../components/layout/notificationStyle'
import { formatRelativeTime, formatDateTime } from '../../utils/format'

const PAGE_SIZE = 25

// The full history behind the bell. The dropdown shows the newest few; anything
// older is read here, where there is room to page through it.
//   GET   /notifications?page&size
//   PATCH /notifications/{id}/read · /notifications/read-all
export default function Notifications() {
  const [page, setPage] = useState(1)
  const qc = useQueryClient()
  const navigate = useNavigate()
  const { notify } = useNotification()

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['notifications', 'page', page],
    queryFn: () => notificationService.list({ page: page - 1, size: PAGE_SIZE }),
    placeholderData: keepPreviousData,
  })

  const rows = data?.content || []
  const total = data?.totalElements ?? 0
  const unread = rows.filter((n) => !n.read).length

  const refresh = () => qc.invalidateQueries({ queryKey: ['notifications'] })

  const markAll = useMutation({
    mutationFn: () => notificationService.markAllRead(),
    onSuccess: refresh,
    onError: (err) => notify.error(err?.message || 'Could not mark notifications as read.'),
  })

  // Marking read must never block the navigation the user asked for.
  const open = async (n) => {
    if (!n.read) {
      try { await notificationService.markRead(n.id) } catch { /* non-blocking */ }
      refresh()
    }
    const to = resolveLink(n.link)
    if (to) navigate(to)
  }

  return (
    <>
      <PageHeader
        title="Notifications"
        subtitle="Everything the system has sent you, newest first."
        breadcrumb={[{ label: 'Home', to: '/dashboard' }, { label: 'Notifications' }]}
        actions={unread > 0 && (
          <Button variant="light" icon={FiCheck} loading={markAll.isPending}
            onClick={() => markAll.mutate()}>Mark all as read</Button>
        )}
      />

      <Card title={`All notifications (${total})`} bodyClassName={rows.length ? 'p-0' : ''}>
        {isLoading ? <Loader label="Loading notifications…" />
          : isError ? <div className="alert alert-danger py-2 small mb-0">{error?.message || 'Could not load notifications.'}</div>
            : rows.length === 0 ? (
              <EmptyState icon={FiBell} title="No notifications yet"
                message="Alerts, plan changes and payment updates appear here as they happen." />
            ) : (
              <ul className="list-unstyled mb-0">
                {rows.map((n) => <NotificationRow key={n.id} n={n} onOpen={open} />)}
              </ul>
            )}
      </Card>

      {(data?.totalPages ?? 1) > 1 && (
        <div className="d-flex justify-content-end mt-3">
          <Pagination page={page} totalPages={data.totalPages} onChange={setPage} />
        </div>
      )}
    </>
  )
}

function NotificationRow({ n, onOpen }) {
  const { icon: Icon, cls, bg } = styleFor(n.type, n.link)
  const clickable = !!resolveLink(n.link) || !n.read

  return (
    <li>
      {/* Same row styling as the bell, so one notification looks like itself
          in both places. A notification with nowhere to go and nothing to mark
          is a plain record — not a control that does nothing when clicked. */}
      <button type="button" className={`notif-item ${n.read ? '' : 'is-unread'}`}
        disabled={!clickable} onClick={() => onOpen(n)}>
        <span className="notif-item__icon" style={{ background: bg }}>
          <Icon className={cls} size={16} />
        </span>
        <span className="notif-item__text">
          <span className={`d-block ${n.read ? '' : 'fw-semibold'}`}>{n.title}</span>
          {n.message && <span className="d-block text-muted small mt-1">{n.message}</span>}
        </span>
        <span className="text-muted small flex-shrink-0 text-nowrap" title={formatDateTime(n.createdAt)}>
          {formatRelativeTime(n.createdAt)}
        </span>
        {!n.read && <span className="notif-item__dot" aria-label="Unread" />}
      </button>
    </li>
  )
}

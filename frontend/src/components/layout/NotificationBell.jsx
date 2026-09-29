import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { FiBell, FiArrowRight } from 'react-icons/fi'
import { notificationService } from '../../services/notificationService'
import { useNotification } from '../../context/NotificationContext'
import { formatRelativeTime } from '../../utils/format'
import { styleFor, resolveLink } from './notificationStyle'

const POLL_MS = 30000
// The dropdown is for "what just happened", not for reading history — with 271
// notifications, scrolling 20 of them in a small panel found nothing. Ten, then
// the full page.
const PAGE_SIZE = 10

export default function NotificationBell() {
  const [open, setOpen] = useState(false)
  const wrapRef = useRef(null)
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { notify } = useNotification()

  // Badge: polled on an interval, and refreshed when the tab regains focus.
  const { data: unread = 0 } = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: () => notificationService.unreadCount(),
    refetchInterval: POLL_MS,
    refetchOnWindowFocus: true,
  })

  // List: only fetched while the dropdown is actually open.
  const { data: page, isLoading } = useQuery({
    queryKey: ['notifications', 'list'],
    queryFn: () => notificationService.list({ page: 0, size: PAGE_SIZE }),
    enabled: open,
  })
  const items = page?.content || []

  // Dismiss on outside click or Escape.
  useEffect(() => {
    if (!open) return undefined
    const onPointerDown = (e) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target)) setOpen(false)
    }
    const onKeyDown = (e) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('mousedown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('mousedown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  const refresh = () => qc.invalidateQueries({ queryKey: ['notifications'] })

  // Mark read, then route to the notification's in-app link. A failed PATCH
  // must not block navigation — the user asked to go somewhere.
  const openItem = async (n) => {
    setOpen(false)
    if (!n.read) {
      try { await notificationService.markRead(n.id) } catch { /* non-blocking */ }
      refresh()
    }
    const to = resolveLink(n.link)
    if (to) navigate(to)
  }

  const viewAll = () => { setOpen(false); navigate('/notifications') }

  const markAll = useMutation({
    mutationFn: () => notificationService.markAllRead(),
    onSuccess: refresh,
    onError: (err) => notify.error(err?.message || 'Could not mark notifications as read.'),
  })

  return (
    <div className="position-relative" ref={wrapRef}>
      <button
        className="icon-btn"
        aria-label={unread > 0 ? `Notifications, ${unread} unread` : 'Notifications'}
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
      >
        <FiBell />
        {unread > 0 && (
          <span className="position-absolute translate-middle badge rounded-pill bg-danger"
            style={{ top: 8, left: '78%', fontSize: 9 }}>
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>

      {open && (
        <div className="notif-panel" role="dialog" aria-label="Notifications">
          <div className="notif-panel__head">
            <span className="fw-semibold">Notifications</span>
            {unread > 0 && (
              <button type="button" className="btn btn-sm btn-link p-0 text-decoration-none"
                disabled={markAll.isPending} onClick={() => markAll.mutate()}>
                Mark all as read
              </button>
            )}
          </div>

          <div className="notif-panel__body">
            {isLoading ? (
              <div className="text-center py-4">
                <span className="spinner-border spinner-border-sm text-primary" role="status" />
              </div>
            ) : items.length === 0 ? (
              <div className="text-center text-muted small py-4">You&apos;re all caught up.</div>
            ) : (
              items.map((n) => <NotificationRow key={n.id} n={n} onOpen={openItem} />)
            )}
          </div>

          {/* Always offered, not only when there are more: "where do I find the
              one from last week" is the question this answers. */}
          <div className="notif-panel__foot">
            <button type="button"
              className="btn btn-sm btn-link p-0 text-decoration-none d-inline-flex align-items-center gap-1"
              onClick={viewAll}>
              View all notifications
              {page?.totalElements > items.length && ` (${page.totalElements})`}
              <FiArrowRight size={13} />
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

function NotificationRow({ n, onOpen }) {
  const { icon: Icon, cls, bg } = styleFor(n.type, n.link)
  return (
    <button type="button" className={`notif-item ${n.read ? '' : 'is-unread'}`} onClick={() => onOpen(n)}>
      <span className="notif-item__icon" style={{ background: bg }}>
        <Icon className={cls} size={15} />
      </span>
      <span className="notif-item__text">
        <span className={`d-block ${n.read ? '' : 'fw-semibold'}`}>{n.title}</span>
        {n.message && <span className="d-block text-muted small mt-1">{n.message}</span>}
        <span className="d-block text-muted mt-1" style={{ fontSize: 11 }}>{formatRelativeTime(n.createdAt)}</span>
      </span>
      {!n.read && <span className="notif-item__dot" aria-hidden="true" />}
    </button>
  )
}

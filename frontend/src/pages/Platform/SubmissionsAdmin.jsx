import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { FiCalendar, FiMessageSquare, FiMail, FiCheck, FiRotateCcw } from 'react-icons/fi'
import PageHeader from '../../components/common/PageHeader'
import Card from '../../components/common/Card'
import DataTable from '../../components/tables/DataTable'
import Pagination from '../../components/common/Pagination'
import Button from '../../components/common/Button'
import StatusBadge from '../../components/common/StatusBadge'
import { siteAdminService } from '../../services/siteAdminService'
import { useNotification } from '../../context/NotificationContext'
import { formatDateTime } from '../../utils/format'

// What visitors sent through the public website: demo requests, contact
// messages and newsletter sign-ups. Demo requests and messages are worked
// through by marking them handled (undoable); subscribers are a plain list.
//
// The tab is in the URL (/platform/submissions/demo-requests …) because the
// LEAD_RECEIVED notification links straight to it.
const TABS = [
  { key: 'demo-requests', label: 'Demo requests', icon: FiCalendar, count: 'demoRequestsPending' },
  { key: 'contact-messages', label: 'Messages', icon: FiMessageSquare, count: 'contactMessagesPending' },
  { key: 'newsletter-subscribers', label: 'Subscribers', icon: FiMail, count: 'newsletterSubscribers' },
]

export default function SubmissionsAdmin() {
  const { tab: tabParam } = useParams()
  const navigate = useNavigate()
  const tab = TABS.some((t) => t.key === tabParam) ? tabParam : TABS[0].key
  const setTab = (key) => navigate(`/platform/submissions/${key}`, { replace: true })
  const summary = useQuery({ queryKey: ['submissions', 'summary'], queryFn: siteAdminService.summary })

  return (
    <>
      <PageHeader title="Website Submissions" subtitle="Platform — demo requests, messages and newsletter sign-ups from the public site."
        breadcrumb={[{ label: 'Home', to: '/platform/clients' }, { label: 'Platform' }, { label: 'Submissions' }]} />

      <div className="d-flex flex-wrap gap-1 mb-3">
        {TABS.map((t) => {
          const n = summary.data?.[t.count]
          return (
            <button key={t.key} type="button"
              className={`btn btn-sm d-inline-flex align-items-center gap-2 ${tab === t.key ? 'btn-primary' : 'btn-light'}`}
              onClick={() => setTab(t.key)}>
              <t.icon size={15} /> {t.label}
              {n > 0 && (
                <span className={`badge ${tab === t.key ? 'text-bg-light' : t.key === 'newsletter-subscribers' ? 'text-bg-secondary' : 'text-bg-danger'}`}
                  title={t.key === 'newsletter-subscribers' ? 'Subscribers' : 'Pending'}>
                  {n}
                </span>
              )}
            </button>
          )
        })}
      </div>

      {/* key: each tab keeps its own paging/filter state from scratch */}
      {tab === 'demo-requests' && <HandledList key="demo" kind="demo" />}
      {tab === 'contact-messages' && <HandledList key="contact" kind="contact" />}
      {tab === 'newsletter-subscribers' && <Subscribers />}
    </>
  )
}

const KINDS = {
  demo: {
    title: 'Demo requests',
    fetch: siteAdminService.demoRequests,
    setHandled: siteAdminService.setDemoHandled,
    extra: [
      { key: 'companyName', header: 'Company', render: (r) => r.companyName || '—' },
      { key: 'phone', header: 'Phone', render: (r) => r.phone || '—' },
    ],
  },
  contact: {
    title: 'Messages',
    fetch: siteAdminService.contactMessages,
    setHandled: siteAdminService.setContactHandled,
    extra: [
      { key: 'subject', header: 'Subject', render: (r) => r.subject || '—' },
    ],
  },
}

// Demo requests and contact messages share one screen: newest first, pending
// by default, and a "Mark handled" that can be taken back.
function HandledList({ kind }) {
  const k = KINDS[kind]
  const qc = useQueryClient()
  const { notify } = useNotification()
  const [pendingOnly, setPendingOnly] = useState(true)
  const [page, setPage] = useState(1)   // 1-based here, 0-based on the API

  const q = useQuery({
    queryKey: ['submissions', kind, pendingOnly, page],
    queryFn: () => k.fetch({ handled: pendingOnly ? false : undefined, page: page - 1, size: 20 }),
    placeholderData: keepPreviousData,
  })

  const mark = useMutation({
    mutationFn: ({ id, handled }) => k.setHandled(id, handled),
    onSuccess: (_, { handled }) => {
      notify.success(handled ? 'Marked as handled' : 'Moved back to pending')
      qc.invalidateQueries({ queryKey: ['submissions'] })   // the list and the tab badges
    },
    onError: (e) => notify.error(e.message || 'Could not update it'),
  })

  const rows = q.data?.content || []
  const columns = [
    { key: 'submittedAt', header: 'Received', minWidth: 140, render: (r) => formatDateTime(r.submittedAt) },
    { key: 'name', header: 'From', minWidth: 180, render: (r) => (
      <div>
        <div className="fw-semibold">{r.name}</div>
        <a href={`mailto:${r.email}`} className="small">{r.email}</a>
      </div>
    ) },
    ...k.extra,
    { key: 'message', header: 'Message', minWidth: 260, render: (r) => (
      r.message
        ? <div className="small" style={{ whiteSpace: 'pre-wrap', maxWidth: 420 }}>{r.message}</div>
        : <span className="text-muted">—</span>
    ) },
    { key: 'handled', header: 'Status', minWidth: 170, render: (r) => (
      r.handled ? (
        <div>
          <StatusBadge status="success" label="Handled" />
          <div className="text-muted small mt-1">
            {r.handledBy && <>by {r.handledBy}<br /></>}{formatDateTime(r.handledAt)}
          </div>
        </div>
      ) : <StatusBadge status="warning" label="Pending" />
    ) },
    { key: 'actions', header: '', render: (r) => {
      const busy = mark.isPending && mark.variables?.id === r.id
      return r.handled ? (
        <Button size="sm" variant="light" icon={FiRotateCcw} loading={busy}
          onClick={() => mark.mutate({ id: r.id, handled: false })}>Undo</Button>
      ) : (
        <Button size="sm" variant="light" icon={FiCheck} loading={busy}
          onClick={() => mark.mutate({ id: r.id, handled: true })}>Mark handled</Button>
      )
    } },
  ]

  return (
    <Card title={`${k.title} (${q.data?.totalElements ?? 0})`}
      actions={(
        <div className="btn-group btn-group-sm">
          {[[true, 'Pending'], [false, 'All']].map(([v, label]) => (
            <button key={label} type="button" className={`btn ${pendingOnly === v ? 'btn-primary' : 'btn-light'}`}
              onClick={() => { setPendingOnly(v); setPage(1) }}>{label}</button>
          ))}
        </div>
      )}>
      <DataTable columns={columns} rows={rows} loading={q.isLoading}
        emptyMessage={pendingOnly ? 'Nothing pending — all caught up.' : 'Nothing received yet.'} />
      <div className="d-flex justify-content-end mt-3">
        <Pagination page={page} totalPages={q.data?.totalPages || 0} onChange={setPage} />
      </div>
    </Card>
  )
}

function Subscribers() {
  const [page, setPage] = useState(1)
  const q = useQuery({
    queryKey: ['submissions', 'subscribers', page],
    queryFn: () => siteAdminService.subscribers({ page: page - 1, size: 50 }),
    placeholderData: keepPreviousData,
  })
  const columns = [
    { key: 'email', header: 'Email', render: (r) => <a href={`mailto:${r.email}`}>{r.email}</a> },
    { key: 'active', header: 'Status', render: (r) => (
      <StatusBadge status={r.active ? 'success' : 'secondary'} label={r.active ? 'Subscribed' : 'Unsubscribed'} />
    ) },
    { key: 'subscribedAt', header: 'Subscribed', render: (r) => formatDateTime(r.subscribedAt) },
  ]
  return (
    <Card title={`Newsletter subscribers (${q.data?.totalElements ?? 0})`}>
      <DataTable columns={columns} rows={q.data?.content || []} loading={q.isLoading}
        emptyMessage="No subscribers yet." />
      <div className="d-flex justify-content-end mt-3">
        <Pagination page={page} totalPages={q.data?.totalPages || 0} onChange={setPage} />
      </div>
    </Card>
  )
}

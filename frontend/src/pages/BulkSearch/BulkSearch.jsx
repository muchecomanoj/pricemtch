import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useQuery, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import {
  FiUploadCloud, FiArrowRight, FiAlertCircle, FiCheckCircle, FiClock,
} from 'react-icons/fi'
import PageHeader from '../../components/common/PageHeader'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import Pagination from '../../components/common/Pagination'
import EmptyState from '../../components/common/EmptyState'
import { SkeletonRows } from '../../components/common/Skeleton'
import BulkSearchModal from './BulkSearchModal'
import BulkRow from './BulkRow'
import { bulkSearchService, RUNNING } from '../../services/bulkSearchService'
import { useDestination } from '../../hooks/useDestination'
import { formatDateTime } from '../../utils/format'

const PAGE_SIZE = 25

// The three outcomes are separate on purpose. "Searched and found nothing" is a
// bad identifier to correct; "couldn't search" is a failure to retry. Merging
// them into one error count tells the user to do the wrong thing for half the
// rows.
const OUTCOMES = [
  ['', 'All rows'],
  ['FOUND', 'Found something'],
  ['NONE_MATCHED', 'Nothing matched'],
  ['NO_LISTINGS', 'No listings'],
  ['NOT_JUDGED', 'Not reviewed'],
  ['ERROR', 'Failed'],
]

const JOB_TONE = {
  COMPLETED: 'green', COMPLETED_WITH_ERRORS: 'orange',
  FAILED: 'red', PROCESSING: 'blue', PENDING: 'slate',
}

function Stat({ label, value, hint, tone }) {
  return (
    <div className="col-6 col-md-3">
      <div className="p-3 rounded-3" style={{ background: 'var(--app-bg)' }}>
        <div className={`h4 mb-0 ${tone || ''}`}>{value ?? 0}</div>
        <div className="fw-semibold small">{label}</div>
        {hint && <div className="text-muted" style={{ fontSize: 11 }}>{hint}</div>}
      </div>
    </div>
  )
}

export default function BulkSearch() {
  const { jobId } = useParams()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { destination } = useDestination()
  const [uploading, setUploading] = useState(false)
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(1)

  const jobQ = useQuery({
    queryKey: ['bulkSearchJob', jobId],
    queryFn: () => bulkSearchService.job(jobId),
    enabled: !!jobId,
    // Rows land as they finish, so the job is re-read while it runs and stops
    // being polled the moment it is done.
    refetchInterval: (q) => (RUNNING.includes(q.state.data?.status) ? 5000 : false),
  })
  const job = jobQ.data
  const running = RUNNING.includes(job?.status)

  const rowsQ = useQuery({
    queryKey: ['bulkSearchRows', jobId, status, page],
    queryFn: () => bulkSearchService.rows(jobId, { status, page: page - 1, size: PAGE_SIZE }),
    enabled: !!jobId,
    placeholderData: keepPreviousData,
    // Re-fetched alongside the job rather than only at the end: on a 100-row
    // file that is the difference between results arriving for half an hour and
    // nothing arriving for half an hour.
    refetchInterval: running ? 5000 : false,
  })

  const historyQ = useQuery({
    queryKey: ['bulkSearchHistory'],
    queryFn: () => bulkSearchService.history({ size: 8 }),
  })

  useEffect(() => { setPage(1) }, [status, jobId])

  const onSaved = () => {
    qc.invalidateQueries({ queryKey: ['bulkSearchRows', jobId] })
    qc.invalidateQueries({ queryKey: ['products'] })
  }

  const rows = rowsQ.data?.content ?? []

  return (
    <>
      <PageHeader title="Bulk Product Search"
        subtitle="Search the marketplaces for a whole spreadsheet of products at once."
        breadcrumb={[{ label: 'Home', to: '/dashboard' }, { label: 'Search', to: '/search' }, { label: 'Bulk' }]}
        actions={(
          <Button icon={FiUploadCloud} onClick={() => setUploading(true)}>Upload a file</Button>
        )} />

      <BulkSearchModal show={uploading} onClose={() => setUploading(false)} destination={destination}
        onStarted={(j) => { setUploading(false); navigate(`/search/bulk/${j.id}`) }} />

      {!jobId ? (
        <Card title="Past runs">
          {historyQ.isLoading ? <SkeletonRows rows={4} />
            : (historyQ.data?.content?.length ?? 0) === 0 ? (
              <EmptyState icon={FiUploadCloud} title="No bulk searches yet"
                message="Upload a spreadsheet of products and every row is searched against the
                         marketplaces. Nothing is added to your catalogue — you save what you want
                         afterwards." />
            ) : (
              <div className="d-flex flex-column gap-2">
                {historyQ.data.content.map((j) => (
                  <Link key={j.id} to={`/search/bulk/${j.id}`}
                    className="result-card text-reset text-decoration-none">
                    <div className="flex-grow-1" style={{ minWidth: 0 }}>
                      <div className="d-flex align-items-center flex-wrap gap-2">
                        <span className={`mr-pill mr-pill--${JOB_TONE[j.status] || 'slate'}`}>{j.status}</span>
                        <span className="fw-semibold">{j.fileName || `Job #${j.id}`}</span>
                      </div>
                      <div className="text-muted small mt-1">
                        {j.totalRows} row{j.totalRows === 1 ? '' : 's'}
                        {' · '}{j.foundRows ?? 0} found · {j.emptyRows ?? 0} empty · {j.errorRows ?? 0} failed
                        {j.startedAt && <> · {formatDateTime(j.startedAt)}</>}
                      </div>
                    </div>
                    <FiArrowRight className="flex-shrink-0" />
                  </Link>
                ))}
              </div>
            )}
        </Card>
      ) : (
        <>
          {/* ── Progress ─────────────────────────────────────────── */}
          <Card className="mb-3"
            title={job?.fileName || `Job #${jobId}`}
            subtitle={job?.judge === false ? 'AI review was skipped — every listing needs checking by hand' : undefined}
            actions={job && (
              <span className={`mr-pill mr-pill--${JOB_TONE[job.status] || 'slate'}`}>{job.status}</span>
            )}>
            {jobQ.isLoading ? <SkeletonRows rows={2} /> : !job ? (
              <div className="alert alert-danger py-2 small mb-0">That job could not be loaded.</div>
            ) : (
              <>
                <div className="progress mb-3" style={{ height: 10 }}>
                  <div className={`progress-bar ${running ? 'progress-bar-striped progress-bar-animated' : ''}`}
                    style={{ width: `${job.totalRows ? Math.round((job.processedRows / job.totalRows) * 100) : 0}%` }} />
                </div>

                <div className="row g-2">
                  <Stat label="Rows" value={job.totalRows} hint={`${job.processedRows ?? 0} searched`} />
                  <Stat label="Found" value={job.foundRows} hint="review and save" tone="text-success" />
                  {/* Not merged with failures: nothing matched is a bad
                      identifier to fix, not a request to retry. */}
                  <Stat label="Nothing matched" value={job.emptyRows} hint="check the identifier" />
                  <Stat label="Failed" value={job.errorRows} hint="worth retrying" tone={job.errorRows ? 'text-danger' : ''} />
                </div>

                {job.message && <div className="text-muted small mt-3">{job.message}</div>}

                <div className="field-note mt-3" style={{ maxWidth: 'none' }}>
                  {running ? <FiClock size={15} /> : <FiCheckCircle size={15} />}
                  <span>
                    {running
                      ? 'Still searching — rows appear here as they finish, and this keeps running if you leave the page.'
                      : 'Finished.'}{' '}
                    <strong>Nothing has been added to your catalogue.</strong> Open a row and use
                    Save to catalogue for the ones you want.
                  </span>
                </div>
              </>
            )}
          </Card>

          {/* ── Rows ─────────────────────────────────────────────── */}
          <Card title={`Rows (${rowsQ.data?.totalElements ?? 0})`}
            actions={(
              <select className="form-select form-select-sm" style={{ maxWidth: 200 }}
                value={status} onChange={(e) => setStatus(e.target.value)}>
                {OUTCOMES.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
              </select>
            )}>
            {rowsQ.isLoading ? <SkeletonRows rows={5} />
              : rows.length === 0 ? (
                <EmptyState icon={FiAlertCircle} title={running ? 'Nothing finished yet' : 'No rows here'}
                  message={running
                    ? 'The first results will appear within a minute or so.'
                    : 'No rows match this filter.'} />
              ) : (
                <div className="d-flex flex-column gap-2">
                  {rows.map((r) => (
                    <BulkRow key={r.id} row={r} jobId={jobId} onSaved={onSaved} />
                  ))}
                </div>
              )}

            {(rowsQ.data?.totalPages ?? 1) > 1 && (
              <div className="d-flex justify-content-end mt-3 pt-3 border-top-soft">
                <Pagination page={page} totalPages={rowsQ.data.totalPages} onChange={setPage} />
              </div>
            )}
          </Card>
        </>
      )}
    </>
  )
}

import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { FiCpu, FiPlay } from 'react-icons/fi'
import PageHeader from '../../components/common/PageHeader'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import DataTable from '../../components/tables/DataTable'
import JsonView from '../../components/common/JsonView'
import StatusBadge from '../../components/common/StatusBadge'
import { aiService } from '../../services/aiService'
import { useNotification } from '../../context/NotificationContext'

// Wires: GET /ai/executions (audit log) + POST /ai/match (tester)
export default function AIActivity() {
  const { data, isLoading } = useQuery({
    queryKey: ['ai', 'executions'],
    queryFn: () => aiService.executions({ page: 0, size: 20 }),
  })
  const rows = Array.isArray(data) ? data : data?.content || []

  const columns = [
    { key: 'id', header: 'ID' },
    { key: 'provider', header: 'Provider', render: (r) => r.provider || r.model || '—' },
    { key: 'operation', header: 'Operation', render: (r) => r.operation || r.capability || '—' },
    { key: 'status', header: 'Status', render: (r) => r.status ? <StatusBadge status={/success|ok|completed/i.test(r.status) ? 'success' : 'secondary'} label={r.status} /> : '—' },
    { key: 'tokens', header: 'Tokens', render: (r) => r.totalTokens ?? r.tokens ?? '—' },
    { key: 'latencyMs', header: 'Latency', render: (r) => (r.latencyMs != null ? `${r.latencyMs} ms` : '—') },
    { key: 'createdAt', header: 'When', render: (r) => (r.createdAt ? new Date(r.createdAt).toLocaleString() : '—') },
  ]

  return (
    <>
      <PageHeader
        title="AI Activity"
        subtitle="AI gateway audit log and match tester."
        breadcrumb={[{ label: 'Home', to: '/dashboard' }, { label: 'AI Activity' }]}
      />
      <div className="row g-3">
        <div className="col-12 col-xl-8">
          <Card title="Executions" subtitle="GET /ai/executions">
            <DataTable columns={columns} rows={rows} loading={isLoading} emptyMessage="No AI executions recorded yet." />
          </Card>
        </div>
        <div className="col-12 col-xl-4"><MatchTester /></div>
      </div>
    </>
  )
}

function MatchTester() {
  const { notify } = useNotification()
  const [payload, setPayload] = useState('{\n  "productId": 1,\n  "candidateId": 1\n}')
  const [result, setResult] = useState(null)
  const [busy, setBusy] = useState(false)

  const run = async () => {
    let body
    try { body = JSON.parse(payload) }
    catch { return notify.error('Payload is not valid JSON') }
    setBusy(true); setResult(null)
    try { setResult(await aiService.match(body)) }
    catch (e) { notify.error(e.message || 'Match failed'); setResult({ error: e.message }) }
    finally { setBusy(false) }
  }

  return (
    <Card title="Match Tester" subtitle="POST /ai/match">
      <label className="form-label d-flex align-items-center gap-2"><FiCpu /> Request payload (JSON)</label>
      <textarea className="form-control font-monospace" rows={6} value={payload} onChange={(e) => setPayload(e.target.value)} style={{ fontSize: 12.5 }} />
      <Button size="sm" className="mt-2" icon={FiPlay} loading={busy} onClick={run}>Run Match</Button>
      {result && <div className="mt-3"><JsonView data={result} maxHeight={220} /></div>}
    </Card>
  )
}

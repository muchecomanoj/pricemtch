import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { FiSearch, FiInfo } from 'react-icons/fi'
import Modal from '../../components/common/Modal'
import Button from '../../components/common/Button'
import StatusBadge from '../../components/common/StatusBadge'
import { marketplaceService } from '../../services/marketplaceService'
import { mpLabel, marketplaceCodes, healthFor, healthBadge, isUsable } from '../../utils/marketplaces'

// Options for POST /products/{id}/search-jobs { markets[], maxResults }.
//
// maxResults is applied PER MARKETPLACE, not as a total — searching 2 channels
// at 20 returns up to 40 listings. Scoping `markets` lets a user avoid burning
// API quota on channels they don't care about (FR-SRCH-001: the plan is built
// from available identifiers and *enabled channels*).
const DEFAULT_MAX = 20

export default function FindCompetitorsModal({ show, onClose, onSearch, busy }) {
  const [maxResults, setMaxResults] = useState(DEFAULT_MAX)
  const [selected, setSelected] = useState([])   // empty => all enabled channels

  const mpQ = useQuery({
    queryKey: ['marketplaces'],
    queryFn: marketplaceService.list,
    enabled: show,
  })
  const healthQ = useQuery({
    queryKey: ['marketplaces', 'health'],
    queryFn: marketplaceService.health,
    enabled: show,
  })

  const codes = marketplaceCodes(mpQ.data)

  const toggle = (code) => setSelected((s) =>
    s.includes(code) ? s.filter((c) => c !== code) : [...s, code])

  const submit = () => {
    const max = Math.min(50, Math.max(1, Number(maxResults) || DEFAULT_MAX))
    onSearch({ markets: selected, maxResults: max })
  }

  const channelCount = selected.length || codes.length || 1

  return (
    <Modal show={show} onClose={onClose} title="Find competitors" size="lg">
      <div className="mb-3">
        <label className="form-label d-block">Marketplaces</label>
        {mpQ.isLoading ? (
          <span className="spinner-border spinner-border-sm text-primary" />
        ) : codes.length === 0 ? (
          <p className="text-muted small mb-0">No marketplace connectors are configured yet.</p>
        ) : (
          <div className="d-flex flex-wrap gap-2">
            {codes.map((code) => {
              const h = healthFor(healthQ.data, code)
              const badge = healthBadge(h)
              return (
                <label key={code} className="marketplace-chip">
                  <input type="checkbox" checked={selected.includes(code)} onChange={() => toggle(code)} />
                  <span>{mpLabel(code)}</span>
                  {!isUsable(h) && <StatusBadge status={badge.tone} label={badge.label} />}
                </label>
              )
            })}
          </div>
        )}
        <div className="form-text">
          {selected.length === 0
            ? 'None selected — every enabled marketplace will be searched.'
            : `Searching ${selected.length} of ${codes.length} marketplaces.`}
        </div>
      </div>

      <div className="row g-3 align-items-end">
        <div className="col-sm-5">
          <label className="form-label">Results per marketplace</label>
          <input type="number" min={1} max={50} className="form-control"
            value={maxResults} onChange={(e) => setMaxResults(e.target.value)} />
        </div>
        <div className="col-sm-7">
          <div className="d-flex align-items-center gap-2 text-muted small">
            <FiInfo className="flex-shrink-0" />
            Up to <strong className="mx-1">{Math.min(50, Math.max(1, Number(maxResults) || 0)) * channelCount}</strong>
            listings across {channelCount} marketplace{channelCount === 1 ? '' : 's'}.
          </div>
        </div>
      </div>

      <div className="d-flex justify-content-end gap-2 mt-4">
        <Button variant="light" onClick={onClose}>Cancel</Button>
        <Button icon={FiSearch} loading={busy} onClick={submit}>Search</Button>
      </div>
    </Modal>
  )
}

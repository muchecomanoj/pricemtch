import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { FiShoppingBag, FiRefreshCw, FiSearch } from 'react-icons/fi'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import StatusBadge from '../../components/common/StatusBadge'
import { marketplaceService } from '../../services/marketplaceService'
import { marketplaceConfigService } from '../../services/marketplaceConfigService'
import { mpLabel, marketplaceCodes, healthFor, healthBadge, isUsable, mpStatusBadge } from '../../utils/marketplaces'
import MarketplaceConfigModal from './MarketplaceConfigModal'

// ── Marketplaces — the sources product searches run against ─────────────────
// Two role-specific views over GET /marketplaces + /marketplaces/health:
//   Super admin → platform integration status (all marketplaces + health).
//   Client      → the marketplaces their searches cover (available + health).
// No enable/disable endpoint exists yet, so both are read-only — no toggle is
// shown until it can actually persist (see utils/marketplaces + the spec).
export default function MarketplacesSettings({ isSuperAdmin }) {
  // Super admin gets the full integration console (credentials, status, test);
  // clients get a read-only view of which sources their searches cover.
  return isSuperAdmin ? <SuperAdminMarketplaces /> : <ClientMarketplaces />
}

// Super-admin integration console — GET /admin/marketplaces (status, capabilities,
// masked credentials) + Configure modal (PUT + POST /test).
function SuperAdminMarketplaces() {
  const listQ = useQuery({ queryKey: ['admin-marketplaces'], queryFn: marketplaceConfigService.list })
  const [configuring, setConfiguring] = useState(null)   // the entry being integrated

  const refresh = () => listQ.refetch()

  // One card per backend entry, and nothing else. This used to append an
  // "Open Web" card marked Connected whenever the API didn't return one —
  // so a channel that has since been removed still showed as available, and
  // anything a user configured from it produced requests the API rejects.
  const shown = Array.isArray(listQ.data) ? listQ.data : []

  return (
    <Card
      title="Marketplaces"
      subtitle="Integrate and monitor the search sources used across all clients."
      actions={(
        <Button size="sm" variant="light" icon={FiRefreshCw} loading={listQ.isFetching} onClick={refresh}>Refresh</Button>
      )}>
      {listQ.isError ? (
        <div className="alert alert-danger py-2 small mb-0">
          Couldn&apos;t load marketplaces. <button className="btn btn-link btn-sm p-0 align-baseline" onClick={refresh}>Retry</button>
        </div>
      ) : listQ.isLoading ? (
        <div className="text-muted small py-3">Loading marketplaces…</div>
      ) : shown.length === 0 ? (
        <div className="text-muted small py-3">No marketplaces available.</div>
      ) : (
        shown.map((e) => {
          const badge = mpStatusBadge(e.status)
          const name = e.name || mpLabel(e.code)
          const connected = e.status === 'CONNECTED'
          return (
            <div className="d-flex align-items-start justify-content-between gap-3 py-3 border-bottom" key={e.code}>
              <div className="d-flex align-items-start gap-3">
                <div className={`icon-box flex-shrink-0 ${connected ? 'icon-grad-primary' : ''}`}
                  style={connected ? undefined : { background: 'var(--app-bg)', color: 'var(--text-secondary)' }}>
                  <FiShoppingBag />
                </div>
                <div>
                  <div className="fw-semibold">{name}</div>
                  {e.description && <div className="text-muted small">{e.description}</div>}
                  {/* Capabilities as chips */}
                  {e.capabilities?.length > 0 && (
                    <div className="d-flex flex-wrap gap-1 mt-1">
                      {e.capabilities.map((c) => <span key={c} className="badge text-bg-light border">{c}</span>)}
                    </div>
                  )}
                  {/* Show the failure reason on ERROR */}
                  {e.status === 'ERROR' && e.lastMessage && <div className="text-danger small mt-1">{e.lastMessage}</div>}
                </div>
              </div>
              <div className="d-flex align-items-center gap-2 flex-shrink-0">
                <StatusBadge status={badge.tone} label={badge.label} />
                <Button size="sm" variant="light" onClick={() => setConfiguring(e)}>Configure</Button>
              </div>
            </div>
          )
        })
      )}

      {configuring && (
        <MarketplaceConfigModal
          show={!!configuring}
          entry={configuring}
          name={configuring.name || mpLabel(configuring.code)}
          onClose={() => setConfiguring(null)}
          onSaved={refresh}
        />
      )}
    </Card>
  )
}

// Client read-only view — which marketplaces their product searches cover, from
// the public /marketplaces + /marketplaces/health endpoints.
function ClientMarketplaces() {
  const listQ = useQuery({ queryKey: ['marketplaces'], queryFn: marketplaceService.list })
  const healthQ = useQuery({ queryKey: ['marketplaces', 'health'], queryFn: () => marketplaceService.health() })

  const codes = marketplaceCodes(listQ.data)
  const health = Array.isArray(healthQ.data) ? healthQ.data : []
  const failed = listQ.isError || healthQ.isError
  const checkedAt = health.find((h) => h.checkedAt)?.checkedAt
  const refresh = () => { listQ.refetch(); healthQ.refetch() }

  return (
    <Card
      title="Marketplaces"
      subtitle="Marketplaces your product searches cover."
      actions={(
        <Button size="sm" variant="light" icon={FiRefreshCw}
          loading={listQ.isFetching || healthQ.isFetching} onClick={refresh}>Refresh</Button>
      )}>
      {failed ? (
        <div className="alert alert-danger py-2 small mb-0">
          Couldn&apos;t load marketplaces. <button className="btn btn-link btn-sm p-0 align-baseline" onClick={refresh}>Retry</button>
        </div>
      ) : listQ.isLoading ? (
        <div className="text-muted small py-3">Loading marketplaces…</div>
      ) : codes.length === 0 ? (
        <div className="text-muted small py-3">No marketplaces available.</div>
      ) : (
        <>
          {codes.map((code) => {
            const h = healthFor(health, code)
            const badge = healthBadge(h)
            const usable = isUsable(h)
            return (
              <div className="d-flex align-items-center justify-content-between gap-3 py-3 border-bottom" key={code}>
                <div className="d-flex align-items-center gap-3">
                  <div className={`icon-box flex-shrink-0 ${usable ? 'icon-grad-primary' : ''}`}
                    style={usable ? undefined : { background: 'var(--app-bg)', color: 'var(--text-secondary)' }}>
                    <FiShoppingBag />
                  </div>
                  <div>
                    <div className="fw-semibold d-flex align-items-center gap-2">
                      {mpLabel(code)}
                      {usable
                        ? <span className="badge text-bg-light text-success d-inline-flex align-items-center gap-1"><FiSearch size={11} /> Included in your searches</span>
                        : <span className="text-muted small">Temporarily unavailable</span>}
                    </div>
                  </div>
                </div>
                <div className="d-flex align-items-center gap-2">
                  <StatusBadge status={badge.tone} label={badge.label} />
                </div>
              </div>
            )
          })}
          {checkedAt && (
            <div className="form-text mt-2">Last checked {new Date(checkedAt).toLocaleString()}.</div>
          )}
        </>
      )}
    </Card>
  )
}

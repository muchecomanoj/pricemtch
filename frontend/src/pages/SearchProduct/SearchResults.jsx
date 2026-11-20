import { Link } from 'react-router-dom'
import { FiBox, FiArrowRight, FiChevronDown, FiChevronUp, FiImage, FiClock, FiX } from 'react-icons/fi'
import Card from '../../components/common/Card'
import EmptyState from '../../components/common/EmptyState'
import StatusBadge from '../../components/common/StatusBadge'
import { mpLabel } from '../../utils/marketplaces'
import { TraceRow } from './SearchTrace'

// The recent-searches chips under the form. Clicking one runs it again exactly.
export function RecentSearches({ recent, busy, onRerun, onForget, onClear }) {
  return (
    <div className="d-flex flex-wrap align-items-center gap-2 mt-3">
      <span className="text-muted small d-inline-flex align-items-center gap-1">
        <FiClock size={13} /> Recent
      </span>
      {recent.map((e) => (
        <span key={`${e.key}|${e.value}|${e.region}|${(e.markets || []).join()}`}
          className="mr-pill mr-pill--slate d-inline-flex align-items-center gap-1">
          <button type="button" className="btn btn-link btn-sm p-0 text-reset text-decoration-none"
            disabled={busy} onClick={() => onRerun(e)}
            title={`Search ${e.key === 'TITLE' ? 'for' : e.key} ${e.value} again${
              e.markets?.length ? ` on ${e.markets.map(mpLabel).join(' and ')}` : ''}`}>
            <span className="fw-semibold">{e.value.length > 32 ? `${e.value.slice(0, 32)}…` : e.value}</span>
            <span className="text-muted ms-1">
              {e.key !== 'TITLE' ? e.key : ''}{e.region ? ` · ${e.region === 'GB' ? 'UK' : e.region}` : ''}
            </span>
          </button>
          <button type="button" className="btn btn-link btn-sm p-0 text-muted" aria-label={`Remove ${e.value}`}
            title="Remove from recent searches" onClick={() => onForget(e)}><FiX size={12} /></button>
        </span>
      ))}
      <button type="button" className="btn btn-link btn-sm p-0 text-muted" onClick={onClear}>Clear</button>
    </div>
  )
}

function CatalogResult({ p }) {
  const img = p.images?.[0]?.url || (typeof p.images?.[0] === 'string' ? p.images[0] : null)
  return (
    <div className="col-12 col-lg-6">
      <div className="result-card">
        {img ? (
          <img src={img} alt={p.title} className="rounded-3 flex-shrink-0"
            style={{ width: 64, height: 64, objectFit: 'cover' }} />
        ) : (
          <div className="rounded-3 flex-shrink-0 d-grid"
            style={{ width: 64, height: 64, placeItems: 'center', background: 'var(--app-bg)', color: 'var(--primary)' }}>
            <FiBox size={22} />
          </div>
        )}
        <div className="flex-grow-1" style={{ minWidth: 0 }}>
          <div className="d-flex align-items-start justify-content-between gap-2">
            <div className="fw-semibold clamp-2">{p.title}</div>
            {p.status && <StatusBadge status={p.status} />}
          </div>
          <div className="text-muted small mt-1">
            {p.brand && <span className="me-2">{p.brand}</span>}
            {p.sku && <span>· SKU {p.sku}</span>}
          </div>
          <Link to={`/products/${p.id}`} className="btn btn-sm btn-light d-inline-flex align-items-center gap-1 mt-2">
            Open in my catalogue <FiArrowRight />
          </Link>
        </div>
      </div>
    </div>
  )
}

// ── Your catalogue ──
export function CatalogCard({ catalog, showTrace, onToggleTrace }) {
  const catalogRows = catalog?.results || []
  return (
    <Card className="mt-3" title={`In your catalogue (${catalog.totalMatches ?? catalogRows.length})`}
      subtitle="Products you already track. Marketplace listings are above.">
      {catalogRows.length > 0 ? (
        <div className="row g-3">{catalogRows.map((p) => <CatalogResult key={p.id} p={p} />)}</div>
      ) : (
        <EmptyState icon={FiBox} title="Not in your catalogue yet"
          message="Nothing here matches. If a marketplace listing above is the right product, add it to your catalogue." />
      )}

      {catalog.trace?.length > 0 && (
        <div className="mt-3 pt-3 border-top-soft">
          <button type="button" className="btn btn-link p-0 text-decoration-none d-flex align-items-center gap-2 fw-semibold"
            onClick={onToggleTrace}>
            {showTrace ? <FiChevronUp /> : <FiChevronDown />} Match trace ({catalog.trace.length} stages)
          </button>
          {showTrace && (
            <div className="mt-2 d-flex flex-column gap-1">
              {catalog.trace.map((t, i) => <TraceRow key={i} t={t} />)}
            </div>
          )}
        </div>
      )}
    </Card>
  )
}

// ── Visually similar, from the image ──
export function ImageHitsCard({ hits }) {
  return (
    <Card className="mt-3" title={`Visually similar in your catalogue (${hits.length})`}
      subtitle="Matched against your own product images — near-identical pictures, not similar items.">
      <div className="row g-3">
        {hits.map((r) => (
          <div className="col-12 col-lg-6" key={r.productId}>
            <Link to={`/products/${r.productId}`} className="result-card text-reset text-decoration-none">
              {r.imageUrl
                ? <img src={r.imageUrl} alt={r.title} className="rounded-3 flex-shrink-0"
                  style={{ width: 64, height: 64, objectFit: 'contain', background: 'var(--app-bg)' }} />
                : <div className="rounded-3 flex-shrink-0 d-grid"
                  style={{ width: 64, height: 64, placeItems: 'center', background: 'var(--app-bg)' }}>
                  <FiImage className="text-muted" />
                </div>}
              <div className="flex-grow-1" style={{ minWidth: 0 }}>
                <div className="fw-semibold clamp-2">{r.title}</div>
                <div className="text-muted small mt-1">
                  {r.sku && <span className="me-2">SKU {r.sku}</span>}
                  {r.brand && <span>· {r.brand}</span>}
                </div>
                <span className="mr-pill mr-pill--blue mt-2 d-inline-block">{r.similarityPct}% match</span>
              </div>
            </Link>
          </div>
        ))}
      </div>
    </Card>
  )
}

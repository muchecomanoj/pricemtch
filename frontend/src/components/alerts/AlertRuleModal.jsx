import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  FiBell, FiAlertTriangle, FiCheckCircle, FiChevronDown, FiChevronUp, FiSearch,
} from 'react-icons/fi'
import Modal from '../common/Modal'
import Button from '../common/Button'
import { CONDITIONS, EXTRA_CHANNELS, conditionMeta, needsThreshold, buildAlertRulePayload } from './alertRules'
import { productIntelService } from '../../services/productIntelService'
import { productService } from '../../services/productService'
import { useNotification } from '../../context/NotificationContext'

// One alert rule, for one product or a hundred — POST /alerts/rules/bulk.
//
// The same dialog on both screens: opened from the Products list it arrives
// with the ticked rows already chosen, opened from the Alerts page it starts
// empty and the products are picked here. One product is just the smallest
// case of the same request, so there is one form to learn and one result
// format — the old single-product form said "created" and could not tell you
// the rule would never fire for want of competitors.
//
// The reply is per product, not one overall verdict, and that is the point:
// 300 rules created where 2 were duplicates and 1 product was deleted in
// another tab is a success, and a single toast cannot say that. Only the rows
// that were NOT created are listed — nobody needs 298 confirmations of the
// thing they just asked for.

const OUTCOME = {
  SKIPPED_DUPLICATE: { tone: 'orange', label: 'Already had this alert' },
  SKIPPED_NO_COMPETITORS: { tone: 'orange', label: 'Skipped — no competitors yet' },
  NOT_FOUND: { tone: 'slate', label: 'Not in your catalogue' },
  FAILED: { tone: 'red', label: 'Failed' },
}

export default function AlertRuleModal({ show, onClose, productIds = [] }) {
  const qc = useQueryClient()
  const { notify } = useNotification()

  const [ids, setIds] = useState(() => new Set(productIds))
  const [search, setSearch] = useState('')
  const [condition, setCondition] = useState('')
  const [thresholdType, setThresholdType] = useState('PERCENT')
  const [thresholdValue, setThresholdValue] = useState(5)
  const [note, setNote] = useState('')
  const [channels, setChannels] = useState([])
  const [skipNoCompetitors, setSkipNoCompetitors] = useState(false)
  const [result, setResult] = useState(null)
  const [openList, setOpenList] = useState(true)

  // Seeded each time it opens, so reopening from a changed selection does not
  // keep the previous one. Deliberately not keyed on productIds: the caller
  // builds that array inline, so it is a new array on every render.
  useEffect(() => {
    if (show) { setIds(new Set(productIds)); setResult(null); setSearch('') }
  }, [show]) // eslint-disable-line react-hooks/exhaustive-deps

  // Same query key as the Alerts page's own product list, so opening this
  // costs no extra request there.
  const productsQ = useQuery({
    queryKey: ['products', 'selector'],
    queryFn: () => productService.list({ page: 1, size: 100 }),
    enabled: show,
  })
  const products = useMemo(() => productsQ.data?.content ?? [], [productsQ.data])

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase()
    if (!q) return products
    return products.filter((p) => `${p.title || ''} ${p.sku || ''}`.toLowerCase().includes(q))
  }, [products, search])

  const meta = conditionMeta(condition)
  const withThreshold = needsThreshold(condition)
  const count = ids.size
  // Chosen products the picker cannot show — a selection made on a later page
  // of the Products list, or one beyond the first hundred. They are still
  // included in the request, so the count must still admit to them.
  const shownSelected = products.filter((p) => ids.has(p.id)).length
  const hiddenSelected = productsQ.data ? Math.max(0, count - shownSelected) : 0

  const toggleProduct = (id) => setIds((s) => {
    const n = new Set(s); n.has(id) ? n.delete(id) : n.add(id); return n
  })
  const selectFiltered = () => setIds((s) => {
    const n = new Set(s); filtered.forEach((p) => n.add(p.id)); return n
  })

  const onConditionChange = (value) => {
    setCondition(value)
    const allowed = conditionMeta(value)?.types || []
    if (allowed.length && !allowed.includes(thresholdType)) setThresholdType(allowed[0])
  }

  const toggleChannel = (code) => setChannels((c) =>
    c.includes(code) ? c.filter((x) => x !== code) : [...c, code])

  const close = () => {
    setCondition(''); setNote(''); setChannels([]); setResult(null); setSkipNoCompetitors(false)
    onClose()
  }

  const create = useMutation({
    mutationFn: () => productIntelService.bulkCreateAlertRules({
      productIds: [...ids],
      ...buildAlertRulePayload({ condition, thresholdType, thresholdValue, note, channels }),
      skipProductsWithoutCompetitors: skipNoCompetitors,
    }),
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: ['alertRules'] })
      // One product, created, nothing to warn about: the result panel would
      // only be a page of confirmation for a thing already confirmed.
      //
      // Both the per-row flag and the summary count are checked. The row flag
      // alone was not enough: a reply that sets only createdWithoutCompetitors
      // would have closed silently, which is exactly the case the warning
      // exists for — a rule that looks created but cannot fire.
      const quiet = res.requested === 1 && res.created === 1
        && !res.createdWithoutCompetitors
        && !(res.results ?? []).some((r) => r.cannotFireYet)
      if (quiet) { notify.success('Alert rule created'); close(); return }

      setResult(res)
      if (res.created > 0) notify.success(`${res.created} alert${res.created === 1 ? '' : 's'} created`)
      else notify.info('No new alerts were created.')
    },
    onError: (e) => notify.error(e?.message || 'The alerts could not be created.'),
  })

  // Only what did not go through. Created rules need no explanation.
  const problems = (result?.results ?? []).filter((r) => r.outcome !== 'CREATED')
  // Rows the backend flagged, falling back to its own count when it reports
  // the total without marking which rows — the warning is worth showing either
  // way, just without the per-product links.
  const flaggedRows = (result?.results ?? []).filter((r) => r.cannotFireYet)
  const cannotFireCount = flaggedRows.length || (result?.createdWithoutCompetitors ?? 0)

  return (
    <Modal show={show} onClose={close} size="lg" scrollable
      title={result ? 'Alerts created' : 'Add an alert rule'}
      footer={result ? (
        <>
          <Button variant="light" onClick={() => setResult(null)}>Create another</Button>
          <Button onClick={close}>Done</Button>
        </>
      ) : (
        <>
          <Button variant="light" onClick={close}>Cancel</Button>
          <Button icon={FiBell} loading={create.isPending} disabled={!condition || !count}
            onClick={() => create.mutate()}>
            {count > 1 ? `Create ${count} alerts` : 'Create alert'}
          </Button>
        </>
      )}>

      {result ? (
        <>
          <div className="d-flex flex-column gap-2">
            <div className="d-flex align-items-center gap-2">
              <FiCheckCircle className="text-success flex-shrink-0" />
              <span><strong>{result.created}</strong> alert{result.created === 1 ? '' : 's'} created</span>
            </div>
            {(result.skipped > 0 || result.failed > 0) && (
              <div className="text-muted small">
                {result.skipped > 0 && <>{result.skipped} skipped</>}
                {result.skipped > 0 && result.failed > 0 && ' · '}
                {result.failed > 0 && <>{result.failed} failed</>}
              </div>
            )}
          </div>

          {/* A rule on a product with no competitors is real but dormant. It is
              kept rather than refused — competitors get added later — so the
              only wrong thing would be to let someone believe it is watching. */}
          {cannotFireCount > 0 && (
            <div className="field-note field-note--warn mt-3" style={{ maxWidth: 'none' }}>
              <FiAlertTriangle size={15} />
              <span>
                {cannotFireCount === 1
                  ? <>This product has <strong>no competitors yet</strong>, so the alert cannot fire
                    until some are added.</>
                  : <>{cannotFireCount} of these have <strong>no competitors yet</strong>, so they
                    cannot fire until some are added.</>}
                {' '}Open the product and use <strong>Find competitors</strong>.
                {flaggedRows.length > 0 && (
                  <span className="d-block mt-1">
                    {flaggedRows.map((r) => (
                      <Link key={r.productId} to={`/products/${r.productId}`} className="me-2">
                        {r.productTitle || `#${r.productId}`}
                      </Link>
                    ))}
                  </span>
                )}
              </span>
            </div>
          )}

          {problems.length > 0 && (
            <div className="mt-3">
              <button type="button" className="btn btn-sm btn-link text-decoration-none p-0 d-inline-flex align-items-center gap-1"
                onClick={() => setOpenList((v) => !v)}>
                {problems.length} product{problems.length === 1 ? '' : 's'} not given an alert
                {openList ? <FiChevronUp size={14} /> : <FiChevronDown size={14} />}
              </button>
              {openList && (
                <ul className="list-unstyled mt-2 mb-0 d-flex flex-column gap-2">
                  {problems.map((r) => {
                    const o = OUTCOME[r.outcome] || { tone: 'slate', label: r.outcome }
                    return (
                      <li key={r.productId} className="d-flex flex-wrap align-items-center gap-2">
                        <span className={`mr-pill mr-pill--${o.tone}`}>{o.label}</span>
                        <Link to={`/products/${r.productId}`} className="fw-semibold text-decoration-none">
                          {r.productTitle || `#${r.productId}`}
                        </Link>
                        {r.message && <span className="text-muted small">{r.message}</span>}
                        {r.outcome === 'SKIPPED_NO_COMPETITORS' && (
                          <Link to={`/products/${r.productId}`} className="small d-inline-flex align-items-center gap-1">
                            <FiSearch size={12} /> Find competitors
                          </Link>
                        )}
                      </li>
                    )
                  })}
                </ul>
              )}
            </div>
          )}
        </>
      ) : (
        <div className="row g-3">
          <div className="col-12">
            <label className="form-label" htmlFor="ar-search">
              Products <span className="text-muted fw-normal">({count} selected)</span>
            </label>
            <input id="ar-search" className="form-control form-control-sm mb-2" value={search}
              placeholder="Search products by name or SKU…" onChange={(e) => setSearch(e.target.value)} />
            <div className="border rounded-3 p-1" style={{ maxHeight: 220, overflowY: 'auto' }}>
              {productsQ.isLoading ? (
                <div className="text-muted small p-2">Loading products…</div>
              ) : filtered.length === 0 ? (
                <div className="text-muted small p-2">
                  {products.length ? 'No products match that search.' : 'No products in your catalogue yet.'}
                </div>
              ) : filtered.map((p) => (
                <label key={p.id} className="d-flex align-items-center gap-2 py-1 px-2 rounded-2"
                  style={{ cursor: 'pointer' }}>
                  <input type="checkbox" className="form-check-input m-0 flex-shrink-0"
                    checked={ids.has(p.id)} onChange={() => toggleProduct(p.id)} />
                  <span className="text-truncate">{p.title || `#${p.id}`}</span>
                  {p.sku && <span className="text-muted small font-monospace ms-auto flex-shrink-0">{p.sku}</span>}
                </label>
              ))}
            </div>
            <div className="d-flex flex-wrap align-items-center gap-3 mt-1">
              {filtered.length > 0 && (
                <button type="button" className="btn btn-sm btn-link p-0 text-decoration-none"
                  onClick={selectFiltered}>
                  Select {search.trim() ? `these ${filtered.length}` : `all ${filtered.length}`}
                </button>
              )}
              {count > 0 && (
                <button type="button" className="btn btn-sm btn-link p-0 text-decoration-none text-muted"
                  onClick={() => setIds(new Set())}>Clear selection</button>
              )}
            </div>
            {hiddenSelected > 0 && (
              <div className="form-text">
                {hiddenSelected} more selected on the Products page, not shown in this list. They are
                included.
              </div>
            )}
          </div>

          <div className={withThreshold ? 'col-md-6' : 'col-12'}>
            <label className="form-label" htmlFor="ar-condition">Condition</label>
            <select id="ar-condition" className="form-select" value={condition}
              onChange={(e) => onConditionChange(e.target.value)}>
              <option value="">Choose a condition…</option>
              {CONDITIONS.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
            </select>
            {meta?.hint && <div className="form-text">{meta.hint}</div>}
          </div>

          {withThreshold && (
            <>
              <div className="col-md-3">
                <label className="form-label" htmlFor="ar-type">Type</label>
                <select id="ar-type" className="form-select" value={thresholdType}
                  onChange={(e) => setThresholdType(e.target.value)}>
                  {meta.types.map((t) => <option key={t}>{t}</option>)}
                </select>
              </div>
              <div className="col-md-3">
                <label className="form-label" htmlFor="ar-value">
                  Value{thresholdType === 'PERCENT' ? ' (%)' : meta.unit ? ` (${meta.unit})` : ''}
                </label>
                <input id="ar-value" type="number" min="0" step="0.1" className="form-control"
                  value={thresholdValue} onChange={(e) => setThresholdValue(e.target.value)} />
              </div>
            </>
          )}

          <div className="col-12">
            <label className="form-label" htmlFor="ar-note">Note (optional)</label>
            <input id="ar-note" className="form-control" value={note} placeholder="Why this rule exists"
              onChange={(e) => setNote(e.target.value)} />
          </div>

          {/* Where this rule is delivered. In-app is always on — an alert
              nobody can find later is worse than a duplicate — so it is shown
              as fixed rather than as an unticked option. */}
          <div className="col-12">
            <label className="form-label">Send to</label>
            <div className="d-flex flex-wrap gap-2">
              <label className="marketplace-chip" style={{ opacity: 0.75, cursor: 'default' }}>
                <input type="checkbox" checked readOnly />
                <span>In-app</span>
              </label>
              {EXTRA_CHANNELS.map(({ code, label }) => (
                <label key={code} className="marketplace-chip">
                  <input type="checkbox" checked={channels.includes(code)}
                    onChange={() => toggleChannel(code)} />
                  <span>{label}</span>
                </label>
              ))}
            </div>
            <div className="form-text">
              Slack and Teams need a webhook in <Link to="/settings">Settings → Notifications</Link>{' '}
              before they deliver anything.
            </div>
          </div>

          {/* Off by default: a rule on a product with no competitors still
              makes sense once some are found, and silently dropping products
              somebody deliberately selected is the worse surprise. */}
          {count > 1 && (
            <div className="col-12">
              <div className="form-check">
                <input className="form-check-input" type="checkbox" id="ar-skip"
                  checked={skipNoCompetitors} onChange={(e) => setSkipNoCompetitors(e.target.checked)} />
                <label className="form-check-label" htmlFor="ar-skip">
                  Skip products that have no competitors yet
                  <span className="form-text d-block mt-0">
                    Otherwise they get the alert too, and it starts firing once competitors are added.
                  </span>
                </label>
              </div>
            </div>
          )}
        </div>
      )}
    </Modal>
  )
}

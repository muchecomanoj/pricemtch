import { useState } from 'react'
import { useWriteLock } from '../../context/AuthContext'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { FiTrash2, FiPlus } from 'react-icons/fi'
import Modal from '../../components/common/Modal'
import Button from '../../components/common/Button'
import { productIntelService } from '../../services/productIntelService'
import { useNotification } from '../../context/NotificationContext'

const CONDITIONS = [
  'PRICE_DROP', 'PRICE_INCREASE', 'COMPETITOR_CHANGE', 'NEW_SELLER',
  'OUT_OF_STOCK', 'MARGIN_BREACH', 'PRICE_GAP', 'DATA_STALE',
]
const THRESHOLD_TYPES = ['PERCENT', 'ABSOLUTE', 'DURATION']

// POST/GET/DELETE /alerts/rules — FR-ALERT-001.
export default function TrackPriceModal({ show, onClose, productId, canManage }) {
  // Plain write buttons lock themselves while the plan is read-only.
  const writeLock = useWriteLock()
  const qc = useQueryClient()
  const { notify } = useNotification()
  const [condition, setCondition] = useState('PRICE_DROP')
  const [thresholdType, setThresholdType] = useState('PERCENT')
  const [thresholdValue, setThresholdValue] = useState(5)

  const rules = useQuery({
    queryKey: ['alertRules', productId],
    queryFn: () => productIntelService.alertRules(productId),
    enabled: show,
  })

  const refresh = () => qc.invalidateQueries({ queryKey: ['alertRules', productId] })

  const create = useMutation({
    mutationFn: () => productIntelService.createAlertRule({
      productId: Number(productId),
      condition,
      thresholdType,
      thresholdValue: Number(thresholdValue),
      note: 'Track price',
    }),
    onSuccess: () => { notify.success('Alert rule created'); refresh() },
    onError: (err) => notify.error(err?.message || 'Could not create the rule.'),
  })

  const remove = useMutation({
    mutationFn: (ruleId) => productIntelService.deleteAlertRule(ruleId),
    onSuccess: () => { notify.success('Rule removed'); refresh() },
    onError: (err) => notify.error(err?.message || 'Could not remove the rule.'),
  })

  const list = rules.data || []

  return (
    <Modal show={show} onClose={onClose} title="Track price" size="lg">
      {canManage && (
        <div className="row g-3 align-items-end mb-3">
          <div className="col-md-5">
            <label className="form-label">Condition</label>
            <select className="form-select" value={condition} onChange={(e) => setCondition(e.target.value)}>
              {CONDITIONS.map((c) => <option key={c} value={c}>{c.replace(/_/g, ' ')}</option>)}
            </select>
          </div>
          <div className="col-md-3">
            <label className="form-label">Threshold type</label>
            <select className="form-select" value={thresholdType} onChange={(e) => setThresholdType(e.target.value)}>
              {THRESHOLD_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
            </select>
          </div>
          <div className="col-md-2">
            <label className="form-label">Value</label>
            <input type="number" min="0" step="0.1" className="form-control"
              value={thresholdValue} onChange={(e) => setThresholdValue(e.target.value)} />
          </div>
          <div className="col-md-2">
            <Button write icon={FiPlus} loading={create.isPending} className="w-100 justify-content-center"
              onClick={() => create.mutate()}>Add</Button>
          </div>
        </div>
      )}

      <div className="form-label">Active rules</div>
      {rules.isLoading ? (
        <div className="text-center py-3"><span className="spinner-border spinner-border-sm text-primary" /></div>
      ) : list.length === 0 ? (
        <p className="text-muted small mb-0">No rules for this product yet.</p>
      ) : (
        <div className="d-flex flex-column gap-2">
          {list.map((r) => (
            <div key={r.id} className="d-flex align-items-center justify-content-between gap-2 p-2 rounded-3"
              style={{ background: 'var(--app-bg)', border: '1px solid var(--border-soft)' }}>
              <span className="small">
                <span className="fw-semibold">{r.condition?.replace(/_/g, ' ')}</span>
                {r.thresholdValue != null && (
                  <span className="text-muted"> — {r.thresholdValue}{r.thresholdType === 'PERCENT' ? '%' : ''}</span>
                )}
              </span>
              {canManage && (
                <button className="icon-btn text-danger" title="Remove" disabled={remove.isPending}
                  onClick={() => remove.mutate(r.id)} {...writeLock}><FiTrash2 /></button>
              )}
            </div>
          ))}
        </div>
      )}

      <p className="form-text mt-3 mb-0">
        Rules are evaluated every hour. When one fires you&apos;ll get a notification in the bell.
      </p>
    </Modal>
  )
}

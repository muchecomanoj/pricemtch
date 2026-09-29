import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { FiSave, FiInfo } from 'react-icons/fi'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import CostFields, { toCostPayload } from '../../components/forms/CostFields'
import CostHistory from './CostHistory'
import { productIntelService } from '../../services/productIntelService'
import { useNotification } from '../../context/NotificationContext'
import { money, pctOf, Stat, CostSourceHint } from './shared'

// Short forms for the results panel — the full sentences live on the form.
const TAX_TREATMENT_SHORT = {
  PASS_THROUGH: 'Tax added at checkout — not deducted from what we receive',
  INCLUSIVE: 'Tax already in the price — deducted from what we receive',
  NONE: 'No sales tax applies',
}

// GET/PUT /products/{id}/cost-profile  +  GET /products/{id}/profitability?price=
export default function CostsProfitTab({ productId, costProfile, loadingCost, canManageCosts, defaultPrice }) {
  const qc = useQueryClient()
  const { notify } = useNotification()

  // Scenario price — profitability requires a price, so default to whatever the
  // page resolved (median competitor price) and let the user model alternatives.
  const [price, setPrice] = useState(defaultPrice ?? '')
  useEffect(() => { if (defaultPrice != null && price === '') setPrice(defaultPrice) }, [defaultPrice, price])

  const profit = useQuery({
    queryKey: ['profitability', productId, price],
    queryFn: () => productIntelService.profitability(productId, price),
    enabled: price !== '' && price != null && Number(price) > 0,
  })

  // Defaults to today, so the common case — "these are the costs from now" —
  // needs no thought, while a backdated or future-dated correction is one edit.
  const today = new Date().toISOString().slice(0, 10)
  const { register, handleSubmit, reset } = useForm({
    defaultValues: { taxTreatment: 'PASS_THROUGH', validFrom: today, ...(costProfile || {}) },
  })
  useEffect(() => {
    if (costProfile) {
      reset({
        taxTreatment: 'PASS_THROUGH',
        ...costProfile,
        validFrom: costProfile.validFrom?.slice(0, 10) || today,
      })
    }
  }, [costProfile, reset, today])

  const save = useMutation({
    mutationFn: (values) => productIntelService.saveCostProfile(
      productId, toCostPayload(values, { includeMeta: true }),
    ),
    onSuccess: () => {
      notify.success('Cost profile saved')
      qc.invalidateQueries({ queryKey: ['costProfile', productId] })
      qc.invalidateQueries({ queryKey: ['profitability', productId] })
      qc.invalidateQueries({ queryKey: ['costProfileHistory', productId] })
    },
    onError: (err) => notify.error(err?.message || 'Could not save the cost profile.'),
  })

  const p = profit.data
  const ccy = costProfile?.currency || p?.currency || 'USD'
  const configured = costProfile?.configured

  return (
    <div className="row g-3">
      <div className="col-12 col-xl-7">
        <Card title="Cost profile" subtitle="Per-unit amounts and percentage rates used for every profit calculation.">
          {!configured && !loadingCost && (
            <div className="alert alert-warning d-flex align-items-center gap-2 py-2 small">
              <FiInfo className="flex-shrink-0" /> No costs saved yet — margin, ROI and break-even stay unavailable until you add them.
            </div>
          )}
          <form onSubmit={handleSubmit((v) => save.mutate(v))} className="row g-3">
            <CostFields register={register} disabled={!canManageCosts} showMeta />

            {canManageCosts && (
              <div className="col-12 d-flex justify-content-end">
                <Button write type="submit" icon={FiSave} loading={save.isPending}>Save cost profile</Button>
              </div>
            )}
          </form>
        </Card>

        <CostHistory productId={productId} />
      </div>

      <div className="col-12 col-xl-5">
        <Card title="Profitability" subtitle="Deterministic calculation — change the price to model a scenario.">
          <label className="form-label">Selling price</label>
          <input type="number" min="0" step="0.01" className="form-control"
            value={price} onChange={(e) => setPrice(e.target.value)} placeholder="Enter a price" />
          <div className="form-text mb-3">
            Defaults to the product&apos;s selling price. Change it to model a scenario — nothing is saved.
          </div>

          {!configured ? (
            <p className="text-muted small mb-0">Add costs to see margin, ROI and break-even.</p>
          ) : profit.isLoading ? (
            <div className="text-center py-3"><span className="spinner-border spinner-border-sm text-primary" /></div>
          ) : !p ? (
            <p className="text-muted small mb-0">Enter a price to calculate.</p>
          ) : (
            <>
              <Stat label="Net revenue" value={money(p.netRevenue, ccy)} />
              <Stat label="Variable cost" value={money(p.variableCost, ccy)} />
              <Stat label="Referral fee" value={money(p.referralFee, ccy)} />
              <Stat label="Payment fee" value={money(p.paymentFee, ccy)} />
              <Stat label="Returns allowance" value={money(p.returnsAllowance, ccy)} />
              <Stat label="Tax" value={money(p.tax, ccy)} />
              {p.taxTreatment && (
                <div className="text-muted mb-2" style={{ fontSize: 11 }}>
                  {TAX_TREATMENT_SHORT[p.taxTreatment] || p.taxTreatment}
                </div>
              )}
              <hr className="my-2" />
              <Stat label="Contribution profit" value={money(p.contributionProfit, ccy)} />
              <Stat label="Gross profit" value={money(p.grossProfit, ccy)} />
              <Stat label="Net profit estimate" value={money(p.netProfitEstimate, ccy)} strong />
              <hr className="my-2" />
              <Stat label="Contribution margin" value={pctOf(p.contributionMarginPct)} strong
                tone={p.contributionMarginPct >= 0 ? 'success' : 'danger'} />
              <Stat label="ROI" value={pctOf(p.roiPct)} strong />
              <Stat label="Break-even price" value={money(p.breakEvenPrice, ccy)} />
              <Stat label="Max acquisition cost" value={money(p.maxAllowableAcquisitionCost, ccy)} />
              <CostSourceHint source={p.costSource} />
              {p.formulaVersion && (
                <div className="text-muted mt-2" style={{ fontSize: 11 }}>Formula {p.formulaVersion}</div>
              )}
            </>
          )}
        </Card>
      </div>
    </div>
  )
}

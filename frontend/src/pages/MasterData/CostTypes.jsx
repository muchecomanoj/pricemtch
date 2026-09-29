import MasterDataPage from './MasterDataPage'
import { costTypeStore } from '../../services/localMasterData'
import { UNIT_COSTS, RATES } from '../../components/forms/CostFields'

// Seeded from the fields the profit engine actually reads, so the list starts
// accurate. Rows added here are documentation only — see the note below.
const DESCRIPTIONS = {
  cogs: 'What you pay for the product itself.',
  inboundFreight: 'Shipping the stock to your warehouse.',
  duty: 'Import tariffs and customs charges.',
  prep: 'Labelling, poly-bagging, kitting.',
  fulfilmentFee: 'Pick, pack and ship charge per unit.',
  storage: 'Warehousing cost apportioned per unit.',
  advertising: 'Ad spend attributed to each unit sold.',
  overheadAllocation: 'Share of fixed business costs.',
  outboundShipping: 'Postage to the customer.',
  referralRatePct: "The marketplace's commission on each sale.",
  paymentFeeRatePct: 'Card and gateway processing charges.',
  returnsAllowancePct: 'Expected refunds, set aside up front.',
  taxRatePct: 'Applicable tax on the sale.',
}

const SEED = [
  ...UNIT_COSTS.map(([field, label]) => ({
    label, field, kind: 'Per unit', description: DESCRIPTIONS[field] || '',
  })),
  ...RATES.map(([field, label]) => ({
    label: label.replace(' %', ''), field, kind: 'Rate', description: DESCRIPTIONS[field] || '',
  })),
]

export default function CostTypes() {
  return (
    <MasterDataPage
      title="Cost Types"
      entity="cost type"
      storeKey="costTypes"
      store={costTypeStore}
      seed={SEED}
      description="The cost components used in every profit calculation."
      // Read-only on purpose: the profit engine reads a fixed set of fields, so
      // an added cost type would never be applied and a deleted one would still
      // be charged. Values are entered in Cost Management, not here.
      readOnly
      readOnlyNote="This set is fixed by the profit engine, so it is reference rather than editable. Enter values in Cost Management for tenant-wide defaults, or on a product's Costs & Profit tab to override them."
      fields={[{ key: 'label' }]}
      columns={[
        { key: 'label', header: 'Cost type', render: (r) => <span className="fw-semibold">{r.label}</span> },
        {
          key: 'kind',
          header: 'Kind',
          render: (r) => (
            <span className={`mr-pill ${r.kind === 'Rate' ? 'mr-pill--purple' : 'mr-pill--blue'}`}>{r.kind}</span>
          ),
        },
        {
          key: 'description',
          header: 'What it covers',
          render: (r) => <span className="text-muted small">{r.description || '—'}</span>,
        },
        { key: 'field', header: 'Field', render: (r) => (r.field ? <code className="small">{r.field}</code> : '—') },
      ]}
    />
  )
}

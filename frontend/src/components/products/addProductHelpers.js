// Shared by AddProductWizard and its step views.

export const KEYS = [
  ['ASIN', 'ASIN'],
  ['UPC', 'UPC'],
  ['EAN', 'EAN'],
  ['GTIN', 'GTIN'],
  ['MPN', 'MPN'],
  ['ITEM_ID', 'eBay item number'],
  ['TITLE', 'Product name'],
  ['URL', 'Product link'],
]
export const LABEL = Object.fromEntries(KEYS)

// Rows shown per marketplace before the group collapses behind "N more".
export const GROUP_PREVIEW = 3

// Which listing a row is, for selection state. eBay ids repeat across
// marketplaces in principle, so both halves are needed.
export const rowKey = (r) => `${r.marketplace}:${r.marketplaceItemId}`

// FR-MATCH-003: only the two confident verdicts carry through as a decision,
// and missing evidence overrides even those. Everything else attaches as a
// CANDIDATE for review, which is what an unconfirmed match is.
export const decisionFor = (r) => (r.missingEvidence?.length
  ? undefined
  : { MATCH: 'MATCHED', EQUIVALENT: 'EQUIVALENT' }[r.decision])

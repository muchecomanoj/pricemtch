// What an alert rule can watch for, and how one is turned into a payload.
//
// Shared by the Alerts page (one product) and the bulk modal on the Products
// list (many products at once). Both send the same body bar the id field, so
// the conditions, their hints and the payload shape live here — two copies
// drift, and a threshold that means days in one place and percent in the other
// is the kind of drift nobody notices until an alert fires wrongly.

// thresholdValue means something different per condition, hence the per-
// condition hint and unit.
export const CONDITIONS = [
  { value: 'PRICE_DROP', label: 'Competitor price drops', types: ['PERCENT', 'ABSOLUTE'], hint: 'Fire when a competitor cuts price by at least this much.' },
  { value: 'PRICE_INCREASE', label: 'Competitor price rises', types: ['PERCENT', 'ABSOLUTE'], hint: 'Fire when a competitor raises price by at least this much.' },
  { value: 'COMPETITOR_CHANGE', label: 'Competitor price changes', types: ['PERCENT', 'ABSOLUTE'], hint: 'Fire on any move of at least this size, up or down.' },
  { value: 'PRICE_GAP', label: 'My price above lowest by', types: ['PERCENT', 'ABSOLUTE'], hint: 'Fire when your price exceeds the lowest competitor by this much.' },
  { value: 'MARGIN_BREACH', label: 'Margin falls below threshold', types: ['PERCENT'], hint: 'Fire when contribution margin drops under this percentage.' },
  { value: 'DATA_STALE', label: 'Competitor data goes stale', types: ['ABSOLUTE'], unit: 'days', hint: 'Fire when nothing has refreshed for this many days.' },
  { value: 'NEW_SELLER', label: 'New competitor appears', types: [] },
  { value: 'OUT_OF_STOCK', label: 'Competitor out of stock', types: [] },
]

export const conditionMeta = (c) => CONDITIONS.find((x) => x.value === c)
export const conditionLabel = (c) => conditionMeta(c)?.label || c?.replace(/_/g, ' ') || '—'
export const needsThreshold = (c) => (conditionMeta(c)?.types.length ?? 0) > 0

// The unit a saved rule's threshold is in.
export const thresholdUnit = (r) => {
  if (r.thresholdType === 'PERCENT') return '%'
  const { unit } = conditionMeta(r.condition) || {}
  return unit ? ` ${unit}` : ''
}

// Opt-in delivery beyond the in-app bell. Both need a webhook saved in
// Settings → Notifications before they deliver anything.
export const EXTRA_CHANNELS = [
  { code: 'EMAIL', label: 'Email' },
  { code: 'SLACK', label: 'Slack' },
  { code: 'TEAMS', label: 'Teams' },
]

export const CHANNEL_LABEL = { IN_APP: 'In-app', EMAIL: 'Email', SLACK: 'Slack', TEAMS: 'Teams' }
export const CHANNEL_TONE = { IN_APP: 'slate', EMAIL: 'blue', SLACK: 'purple', TEAMS: 'blue' }

// Where a rule delivers. An empty list means in-app only — that is the
// backend's default, not an absence of routing, so it renders as In-app
// rather than a dash that would read like "nowhere".
export function ChannelPills({ channels }) {
  const list = channels?.length ? channels : ['IN_APP']
  return (
    <span className="d-inline-flex flex-wrap gap-1">
      {list.map((c) => (
        <span key={c} className={`mr-pill mr-pill--${CHANNEL_TONE[c] || 'slate'}`}>
          {CHANNEL_LABEL[c] || c}
        </span>
      ))}
    </span>
  )
}

// The rule half of the request — everything but which product(s) it is for.
//
// A threshold is sent only for conditions that use one: "a new seller
// appeared, by at least 5%" is not a thing. Channels are omitted entirely when
// nothing extra was ticked, so an untouched form takes the backend's own
// in-app default rather than restating it.
export function buildAlertRulePayload({ condition, thresholdType, thresholdValue, note, channels }) {
  return {
    condition,
    ...(needsThreshold(condition) && thresholdValue !== '' && thresholdValue != null
      ? { thresholdType, thresholdValue: Number(thresholdValue) }
      : {}),
    note: note?.trim() || undefined,
    ...(channels?.length ? { channels: ['IN_APP', ...channels] } : {}),
  }
}

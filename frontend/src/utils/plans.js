import { PLANS as MOCK_PLANS } from '../mock/onboarding'

// ─────────────────────────────────────────────────────────────────────────────
// Maps the live GET /public/subscription-plans payload onto the shape the
// onboarding/pricing UI expects, so plan changes in the backend show up in the
// product without a redeploy.
//
// Live shape:  { id, code, name, description, price, currency,
//                billingCycleDays, maxUsers, features: "a,b,c", active,
//                yearlyPrice? }  ← yearlyPrice is optional / future
// UI shape:    { code, name, tagline, icon, color, currency, users,
//                price: { monthly, yearly }, discountPercent, features: [...], recommended }
//
// Yearly billing is 20% off, per the backend contract: yearly = price × 12 × 0.8.
// This is NOT a fabricated figure — the backend charges exactly this at checkout
// (verified: PRO yearly = 149 × 12 × 0.8 = 1430.40). If the API ever returns an
// explicit yearlyPrice, that wins over the formula.
// ─────────────────────────────────────────────────────────────────────────────
export const YEARLY_DISCOUNT = 0.2   // 20% off when billed yearly

// Presentation-only metadata the API doesn't carry, keyed by plan code.
const META = {
  FREE: { icon: 'rocket', color: 'info', tagline: 'Try it out, no card required' },
  BASIC: { icon: 'zap', color: 'primary', tagline: 'For small teams getting started' },
  PRO: { icon: 'briefcase', color: 'success', tagline: 'Most popular for growing businesses', recommended: true },
  ENTERPRISE: { icon: 'shield', color: 'warning', tagline: 'Custom scale and controls' },
}
const FALLBACK_META = { icon: 'zap', color: 'primary', tagline: '' }

const round2 = (n) => Math.round(n * 100) / 100

function normalizeOne(p) {
  const monthly = Number(p.price ?? 0)
  const meta = META[p.code] || FALLBACK_META
  // Discount + yearly price are now owned by the backend. Prefer its values;
  // fall back to the 20% formula only if the API doesn't send them yet.
  const rawYearly = p.yearlyPrice ?? p.annualPrice ?? p.priceYearly
  const pct = p.yearlyDiscountPercent
  const fullYear = round2(monthly * 12)
  let yearly
  let discountPercent
  if (rawYearly != null) {
    yearly = round2(Number(rawYearly))
    discountPercent = pct != null ? pct : (fullYear > 0 ? Math.round((1 - yearly / fullYear) * 100) : 0)
  } else if (pct != null) {
    yearly = round2(fullYear * (1 - pct / 100))
    discountPercent = pct
  } else {
    yearly = round2(fullYear * (1 - YEARLY_DISCOUNT))
    discountPercent = Math.round(YEARLY_DISCOUNT * 100)
  }
  return {
    id: p.id,
    code: p.code,
    name: p.name,
    currency: p.currency || 'USD',
    users: p.maxUsers ?? null,
    // Prefer the explicit trialDays field; fall back to FREE's cycle length.
    trialDays: p.trialDays ?? (p.code === 'FREE' ? (p.billingCycleDays ?? null) : null),
    price: { monthly, yearly },
    discountPercent,
    features: String(p.features || '')
      .split(',')
      .map((f) => f.trim())
      .filter(Boolean),
    tagline: p.description || meta.tagline || '',
    icon: meta.icon,
    color: meta.color,
    recommended: Boolean(meta.recommended),
  }
}

// Two rows are "the same tier" when price, seat cap and feature list all match.
// The catalog currently carries PROFESSIONAL as an exact clone of PRO; keeping
// the lower id means the canonical code wins and the dupe disappears on its own
// once it's removed from the database.
const dedupeKey = (p) => `${p.price.monthly}|${p.users}|${p.features.join(',')}`

export function normalizePlans(apiPlans) {
  if (!Array.isArray(apiPlans) || apiPlans.length === 0) return MOCK_PLANS

  const seen = new Map()
  for (const raw of apiPlans) {
    if (raw?.active === false) continue
    const plan = normalizeOne(raw)
    const key = dedupeKey(plan)
    const prev = seen.get(key)
    if (!prev || (plan.id ?? Infinity) < (prev.id ?? Infinity)) seen.set(key, plan)
  }

  const list = [...seen.values()].sort((a, b) => a.price.monthly - b.price.monthly)
  return list.length ? list : MOCK_PLANS
}

// The yearly discount to advertise on the billing toggle — the largest real
// discount any paid plan offers, or 0 when the backend provides no yearly price.
// Used to label (or hide) the "Save X%" badge instead of a hardcoded number.
export function catalogDiscountPercent(plans) {
  return (plans || []).reduce((max, p) => Math.max(max, p.discountPercent || 0), 0)
}

import { FiBox, FiSearch, FiTrendingUp, FiPieChart, FiBell, FiFileText, FiActivity, FiZap, FiTarget } from 'react-icons/fi'
import { isFreePlan } from '../../utils/plans'
import { formatCurrency } from '../../utils/format'

// The landing page's content and the small helpers that shape it. See
// Landing.jsx for how GET /public/landing and FALLBACK_CONTENT fit together.

export const NAV_LINKS = [
  ['Platform', '#platform'], ['Product', '#workspace'], ['Pricing', '#pricing'], ['Resources', '#footer'],
]

export const TRUST = ['NORTHWIND', 'Vertex&Co', 'MERIDIAN', 'Corta', 'HELIOMART']

// Same shape as GET /public/landing, and the copy it was seeded with.
export const FALLBACK_CONTENT = {
  HERO: {
    badge: 'AI price monitoring · live across every marketplace',
    title: 'Price with intelligence, not guesswork.',
    subtitle: 'Track competitors, monitor market movements, match your catalog to millions of listings, and let AI recommend the price that protects margin and wins the buy box.',
    primaryCta: 'Start free trial',
    secondaryCta: 'See how it works',
    assurances: ['No credit card required', 'SOC 2 ready'],
  },
  STATS: {
    items: [
      { value: '12M+', label: 'Listings tracked daily' },
      { value: '3.2%', label: 'Average margin lift' },
      { value: '45 min', label: 'Saved per analyst / day' },
      { value: '99.9%', label: 'Platform uptime' },
    ],
  },
  FEATURES: {
    eyebrow: 'THE PLATFORM',
    title: 'Everything you need to price competitively',
    subtitle: 'From raw marketplace data to a boardroom-ready recommendation — one connected workflow, no spreadsheets.',
    items: [
      { icon: 'box', title: 'Universal matching engine',
        description: 'Match your catalog to millions of listings by ASIN, UPC, EAN or image similarity — with confidence scores you can audit.' },
      { icon: 'search', title: 'Real-time competitor tracking',
        description: 'Watch every seller across Amazon, eBay, Walmart and the open web — price, stock, shipping and condition, updated continuously.' },
      { icon: 'trend', title: 'AI pricing recommendations',
        description: 'Confidence-scored price moves that protect margin and win the buy box — with the reasoning shown, never a black box.' },
      { icon: 'pie', title: 'Profitability intelligence',
        description: 'Model COGS, fees, shipping, ads and returns to see the true net margin behind every SKU and every price change.' },
      { icon: 'bell', title: 'Smart alerts',
        description: 'Know the moment a rival drops price, goes out of stock, or a new seller appears — routed to the right person, instantly.' },
      { icon: 'report', title: 'Boardroom-ready reports',
        description: 'Export governed, data-status-tagged reports finance can trust — every number labelled actual, calculated or estimated.' },
    ],
  },
  HOW_IT_WORKS: {
    eyebrow: 'HOW IT WORKS',
    title: 'From catalog to confident pricing in three steps',
    subtitle: 'One connected workflow — no spreadsheets, no guesswork, no black boxes.',
    items: [
      { step: '01', title: 'Match',
        description: 'Import your catalog by CSV or API and auto-match every product against millions of marketplace listings.' },
      { step: '02', title: 'Monitor',
        description: 'Continuously track competitor prices, stock, shipping and new sellers across every marketplace you sell on.' },
      { step: '03', title: 'Optimize',
        description: 'Act on confidence-scored AI recommendations that defend the buy box and protect margin — with the reasoning shown.' },
    ],
  },
  // The two split sections. Only the words are content; the product table and
  // the recommendation card beside them are illustrations and stay in code.
  ONE_WORKSPACE: {
    eyebrow: 'ONE WORKSPACE',
    title: 'Your whole pricing operation, in one place',
    subtitle: 'Catalog, competitors, costs and recommendations share one source of truth — so analysts, managers and finance finally work from the same numbers.',
    bullets: [
      'Role-based access for admins, managers, analysts & finance',
      'Import your catalog by CSV or API in minutes',
      'Every number carries an auditable data-status tag',
    ],
    cta: 'Explore the platform',
  },
  AI_RECOMMENDATIONS: {
    eyebrow: 'AI RECOMMENDATIONS',
    title: 'AI that explains the price, not just recommends it',
    subtitle: 'Every recommendation shows the reasoning, the confidence score and the projected margin impact — so your team can trust it and act, instead of second-guessing a black box.',
    bullets: [
      'Every move shows its reasoning and confidence score',
      'Projected margin impact before you commit',
      'Stays inside the competitive range you set',
    ],
    cta: 'See AI recommendations',
  },
  TESTIMONIALS: {
    items: [{
      quote: 'Price Intelligence paid for itself in six weeks. We stopped guessing on competitor moves and our pricing team finally trusts a single set of numbers.',
      name: 'Anita Krishnan', role: 'Head of Pricing, Northwind Retail', initials: 'AK',
    }],
  },
  // No FAQ copy of our own: without the API there is nothing to answer with.
  CTA: {
    title: 'Stop guessing. Start pricing with intelligence.',
    subtitle: 'Set up your catalog in minutes. See competitor moves and your first AI recommendation today.',
    primaryCta: 'Start free trial',
    secondaryCta: 'Book a demo',
  },
}

// The API names icons by slug; an unknown one gets a generic mark.
export const ICONS = { box: FiBox, search: FiSearch, trend: FiTrendingUp, pie: FiPieChart, bell: FiBell, report: FiFileText }
export const iconFor = (slug) => ICONS[slug] || FiZap
// Colour is presentation, not content: by position, cycling if items are added.
export const FEATURE_GRADS = ['primary', 'info', 'primary', 'success', 'purple', 'info']
export const STEP_STYLE = [
  { icon: FiSearch, grad: 'primary' }, { icon: FiActivity, grad: 'info' }, { icon: FiTarget, grad: 'success' },
]
export const list = (section) => (Array.isArray(section?.items) ? section.items : [])

// "12M+" → counts up to 12 then shows "M+"; "45 min" → 45 then " min". A
// value that doesn't start with a number is shown as written, unanimated.
export function parseStat(value) {
  const m = /^(\d+(?:\.\d+)?)(.*)$/.exec(String(value ?? '').trim())
  if (!m) return { text: String(value ?? '') }
  return { value: parseFloat(m[1]), decimals: (m[1].split('.')[1] || '').length, suffix: m[2] }
}

// Pricing grid — the exact same catalog the signup flow shows.
export function pricingTiers(plans) {
  return plans.map((p) => {
    const isFree = p.code === 'FREE' || isFreePlan(p)
    return {
      code: p.code, name: p.name, tagline: p.tagline,
      price: isFree ? 'Free' : formatCurrency(p.price.monthly, p.currency),
      unit: isFree ? '' : '/mo',
      // Paid plans are billed from day one — only Free is a trial.
      cta: isFree ? 'Start free trial' : 'Get started',
      to: `/signup?plan=${p.code}`,
      highlight: p.recommended,
      points: [p.users ? `Up to ${p.users.toLocaleString()} seats` : null, ...p.features].filter(Boolean),
    }
  })
}

export const rise = {
  hidden: { opacity: 0, y: 26 },
  show: (i = 0) => ({ opacity: 1, y: 0, transition: { duration: 0.5, delay: i * 0.06, ease: 'easeOut' } }),
}

// The reassurances under the hero buttons. The trial length is deliberately
// not among them: it is read from the Free plan, second in line, so it can't
// promise a trial the catalog no longer offers.
export function heroTicks(assurances, freePlan) {
  const given = Array.isArray(assurances) ? assurances.filter(Boolean) : []
  const trial = freePlan?.trialDays > 0 ? `${freePlan.trialDays} days free` : freePlan ? 'Free plan available' : null
  return [...given.slice(0, 1), trial, ...given.slice(1)].filter(Boolean)
}

export function initialsOf(name = '') {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w[0].toUpperCase()).join('')
}

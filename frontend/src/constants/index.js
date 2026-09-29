// Centralized constants used across the app.

export const APP_NAME = 'Price Intelligence'

// Role identifiers. The tenant permission level comes from the backend
// `accessType` enum; the platform owner is flagged by `superAdmin`.
export const ROLES = {
  SUPER_ADMIN: 'SUPER_ADMIN', // platform owner (superAdmin=true)
  ADMIN: 'ADMIN',             // tenant administrator
  MANAGER: 'MANAGER',         // pricing manager
  ANALYST: 'ANALYST',         // marketplace analyst
  FINANCE: 'FINANCE',         // finance user
  VIEWER: 'VIEWER',           // read-only
}

// Platform owner — operates the whole SaaS (all client companies + plans).
export const PLATFORM_ROLES = [ROLES.SUPER_ADMIN]

// Tenant administrator — manages ONE company (users, company, full product app).
export const ADMIN_ROLES = [ROLES.ADMIN]

// accessType values assignable to a tenant user (backend CreateUserRequest enum).
export const ASSIGNABLE_ROLES = [
  ROLES.ADMIN,
  ROLES.MANAGER,
  ROLES.ANALYST,
  ROLES.FINANCE,
  ROLES.VIEWER,
]

// ── Capabilities (mirrors the role-permission matrix) ───────────
// Fine-grained, ACTION-level permissions. Used by `can()` in AuthContext to
// show/hide buttons — not just whole screens. Roles: ADMIN=TENANT_ADMIN,
// MANAGER=PRICING_MANAGER, ANALYST=MARKETPLACE_ANALYST, FINANCE=FINANCE_USER,
// VIEWER=READ_ONLY_USER.
const R = ROLES
export const CAPABILITIES = {
  MANAGE_CLIENTS:     [...PLATFORM_ROLES],                          // super admin
  MANAGE_PLANS:       [...PLATFORM_ROLES],                          // super admin
  MANAGE_USERS:       [R.ADMIN],                                    // admin
  MANAGE_COMPANY:     [R.ADMIN],                                    // admin
  // Outbound email (SMTP). A tenant owns its own domain and credentials, so its
  // admin configures it directly — the platform owner should never be handling
  // a client's mail password. Super admin also has it, for the platform default.
  MANAGE_EMAIL:       [R.ADMIN, ...PLATFORM_ROLES],
  MANAGE_PRODUCTS:    [R.ADMIN, R.MANAGER],                         // create/edit/delete
  IMPORT_PRODUCTS:    [R.ADMIN, R.MANAGER],                         // CSV/Excel import
  MARKETPLACE_INGEST: [R.ADMIN, R.MANAGER],                         // Amazon/eBay ingest
  AI_MATCH:           [R.ADMIN, R.ANALYST],                         // AI matching
  MANAGE_COSTS:       [R.ADMIN, R.MANAGER, R.FINANCE],              // costs/profit
  MANAGE_PRICING:     [R.ADMIN, R.MANAGER],                         // recommendations/alerts
  VIEW_PRODUCTS:      [R.ADMIN, R.MANAGER, R.ANALYST, R.FINANCE, R.VIEWER], // all tenant roles
}

// Can this user open a given screen? (mirrors ProtectedRoute's check)
export function canAccessPath(user, path) {
  const allowed = SCREEN_ACCESS[path]
  if (allowed === undefined) return false          // unknown/deep path → not a landing target
  if (allowed === null) return true                // open to all authenticated
  const roles = user?.roles?.length ? user.roles : user?.role ? [user.role] : []
  return roles.some((r) => allowed.includes(r))
}

// Landing screen after login. Always returns a screen the user CAN access, so
// no one is ever dropped onto a 403. Platform owners start at Clients.
export function homePathForUser(user) {
  const roles = user?.roles?.length ? user.roles : user?.role ? [user.role] : []
  const isPlatform = user?.superAdmin || roles.some((r) => PLATFORM_ROLES.includes(r))
  const order = isPlatform
    ? ['/platform/clients', '/platform/plans', '/settings']
    : ['/dashboard', '/products', '/reports', '/settings']
  return order.find((p) => canAccessPath(user, p)) || '/settings'
}

// Where a fresh login lands.
//
// Back to the page that sent someone to the login screen — but only when that
// page was somewhere they were trying to GET TO. Settings is where the profile
// lives, and it is the one screen every user may open, so a session that ended
// there (a logout, an expiry) sent every next login back to the profile
// instead of the dashboard. That is where a session stopped, not a
// destination, so it never counts.
const NOT_A_RETURN_TARGET = ['/settings', '/login', '/two-factor']

export function postLoginPath(user, from) {
  const usable = from
    && !NOT_A_RETURN_TARGET.some((p) => from === p || from.startsWith(`${p}/`))
    && canAccessPath(user, from)
  return usable ? from : homePathForUser(user)
}

// Screen (view) access. Action-level rules live in CAPABILITIES above.
// `null` = any authenticated user.
export const SCREEN_ACCESS = {
  '/dashboard': CAPABILITIES.VIEW_PRODUCTS,           // tenant users (not platform owner)
  '/products': CAPABILITIES.VIEW_PRODUCTS,            // all tenant roles can view
  '/search': [R.ADMIN, R.MANAGER, R.ANALYST],
  '/competitors': CAPABILITIES.VIEW_PRODUCTS,
  // Tracked items and their movements are observations of public marketplace
  // data, so they read like any other product view.
  '/tracked-items': CAPABILITIES.VIEW_PRODUCTS,
  // Bulk search spends marketplace and AI quota per row and can create
  // products, so it follows the search permission, not plain viewing.
  '/search/bulk': CAPABILITIES.MANAGE_PRODUCTS,
  '/price-changes': CAPABILITIES.VIEW_PRODUCTS,
  '/marketplace-tools': CAPABILITIES.MARKETPLACE_INGEST,
  '/match-review': CAPABILITIES.AI_MATCH,
  // Monitors configure scheduled searching, so the gate matches the manual
  // search it automates rather than the read-only listing views.
  '/monitors': CAPABILITIES.MANAGE_PRODUCTS,
  '/price-intelligence': CAPABILITIES.VIEW_PRODUCTS,
  '/costs': CAPABILITIES.MANAGE_COSTS,
  '/profitability': CAPABILITIES.MANAGE_COSTS,
  '/recommendations': CAPABILITIES.MANAGE_PRICING,
  '/alerts': CAPABILITIES.MANAGE_PRICING,
  '/reports': CAPABILITIES.VIEW_PRODUCTS,
  '/ai-analyst': [R.ADMIN, R.MANAGER, R.ANALYST],
  '/ai-activity': CAPABILITIES.MANAGE_USERS,
  '/users': CAPABILITIES.MANAGE_USERS,
  '/roles': CAPABILITIES.MANAGE_USERS,
  '/settings': null,
  // Platform owner only
  '/platform/clients': CAPABILITIES.MANAGE_CLIENTS,
  '/platform/plans': CAPABILITIES.MANAGE_PLANS,
  // Tenant self-service (admin manages their own company)
  '/company': CAPABILITIES.MANAGE_COMPANY,
  '/my-subscription': CAPABILITIES.MANAGE_COMPANY,
}

// Human-friendly labels for role badges/dropdowns.
export const ROLE_LABELS = {
  SUPER_ADMIN: 'Super Admin',
  ADMIN: 'Admin',
  MANAGER: 'Manager',
  ANALYST: 'Analyst',
  FINANCE: 'Finance',
  VIEWER: 'Viewer',
}

// MOCK SEED ONLY. The live channel list comes from GET /marketplaces via
// useMarketplaceCodes() — never from here. This previously listed Walmart
// (never implemented) and Web (removed), and any screen filtering by those
// values sent requests the API rejected.
export const MARKETPLACES = ['Amazon', 'eBay']

// IANA timezones for the client/company forms. Uses the browser's full list
// when available, with a curated fallback for older engines.
const FALLBACK_TIMEZONES = [
  'UTC',
  'America/New_York', 'America/Chicago', 'America/Denver', 'America/Los_Angeles',
  'America/Toronto', 'America/Mexico_City', 'America/Sao_Paulo',
  'Europe/London', 'Europe/Dublin', 'Europe/Paris', 'Europe/Berlin', 'Europe/Madrid',
  'Europe/Rome', 'Europe/Amsterdam', 'Europe/Stockholm', 'Europe/Warsaw', 'Europe/Moscow',
  'Africa/Cairo', 'Africa/Lagos', 'Africa/Johannesburg',
  'Asia/Dubai', 'Asia/Karachi', 'Asia/Kolkata', 'Asia/Dhaka', 'Asia/Bangkok',
  'Asia/Singapore', 'Asia/Hong_Kong', 'Asia/Shanghai', 'Asia/Tokyo', 'Asia/Seoul',
  'Australia/Perth', 'Australia/Sydney', 'Australia/Melbourne', 'Pacific/Auckland',
]

export const TIMEZONES = (() => {
  try {
    if (typeof Intl.supportedValuesOf === 'function') {
      const all = Intl.supportedValuesOf('timeZone')
      if (all?.length) return all
    }
  } catch { /* fall through */ }
  return FALLBACK_TIMEZONES
})()

// The visitor's timezone — handy as a sensible default.
export const DEFAULT_TIMEZONE = (() => {
  try { return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC' } catch { return 'UTC' }
})()

// "Asia/Kolkata (UTC+05:30)" — label for a timezone option.
export function timezoneLabel(tz) {
  try {
    const parts = new Intl.DateTimeFormat('en-US', { timeZone: tz, timeZoneName: 'shortOffset' })
      .formatToParts(new Date())
    const offset = parts.find((p) => p.type === 'timeZoneName')?.value
    return offset ? `${tz.replace(/_/g, ' ')} (${offset})` : tz.replace(/_/g, ' ')
  } catch {
    return tz.replace(/_/g, ' ')
  }
}

export const SEARCH_KEYS = ['SKU', 'ASIN', 'UPC', 'EAN', 'GTIN', 'MPN', 'Title', 'URL']

// Every displayed number carries a data status (per FRD FR-REPORT-001).
export const VALUE_STATUS = {
  ACTUAL: 'actual',
  CALCULATED: 'calculated',
  ESTIMATED: 'estimated',
  UNAVAILABLE: 'unavailable',
  STALE: 'stale',
}

export const PAGE_SIZE = 10

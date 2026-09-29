// Translates backend DTOs -> the shapes the existing UI components expect.
// Keeping this here means components never change when the contract evolves.

// Backend roles may be ['ADMIN'] or [{ name: 'ROLE_ADMIN' }]. Normalize to 'ADMIN'.
function normalizeRole(role) {
  const raw = typeof role === 'string' ? role : role?.name || role?.authority || ''
  return raw.replace(/^ROLE_/, '').toUpperCase()
}

// UserResponse -> { id, name, email, role, roles, active, ... }
// Handles both the new shape (single `role` + `superAdmin` + `status`) and the
// older shape (`roles` array + `enabled`).
export function mapUser(u) {
  if (!u) return null

  // Effective role: platform owner => SUPER_ADMIN; tenant user => accessType.
  // Falls back to legacy `role`/`roles` fields for older payloads.
  const effective = u.superAdmin
    ? 'SUPER_ADMIN'
    : normalizeRole(u.accessType || u.role || u.roles?.[0] || '')
  const roles = effective ? [effective] : []

  // Active flag: prefer `status`, fall back to `enabled`.
  const active = u.status != null ? /^active$/i.test(u.status) : (u.enabled ?? true)

  return {
    id: u.id,
    name: [u.firstName, u.lastName].filter(Boolean).join(' ') || u.email,
    firstName: u.firstName,
    lastName: u.lastName,
    email: u.email,
    phone: u.phone ?? null,
    role: effective || 'VIEWER',
    roles,
    accessType: u.accessType ?? null,
    superAdmin: !!u.superAdmin,
    active,
    status: u.status ?? (u.enabled ? 'ACTIVE' : 'INACTIVE'),
    tenantId: u.tenantId ?? null,
    profileImage: u.profileImage ?? null,
    // What the company's subscription lets it do: FULL, READ_ONLY or BLOCKED,
    // plus the sentence explaining why. Absent for the platform owner, who is
    // never restricted — callers treat missing as FULL.
    account: u.account ?? null,
    createdAt: u.createdAt,
    lastLogin: u.lastLogin,
  }
}

// AuthResponse -> { token, refreshToken, user }
export function mapAuth(a) {
  return {
    token: a.accessToken,
    refreshToken: a.refreshToken,
    user: mapUser(a.user),
  }
}

// ProductResponse -> shape used by Products table / details.
export function mapProduct(p) {
  if (!p) return null
  const idents = p.identifiers || []
  const findId = (t) => idents.find((i) => i.type === t)?.normalizedValue || idents.find((i) => i.type === t)?.originalValue
  const marketplaces = [...new Set(idents.map((i) => i.marketplace).filter(Boolean))]
  // Variant attributes live in attributes[0] — flattened for the form (FR-PROD-003).
  const attr = p.attributes?.[0] || {}
  return {
    id: p.id,
    sku: p.sku,
    asin: findId('ASIN') || '',
    ean: findId('EAN') || '',
    upc: findId('UPC') || '',
    gtin: findId('GTIN') || '',
    mpn: findId('MPN') || '',
    // eBay sells by item number, not ASIN, so an eBay-only product carries a
    // MARKETPLACE_ID identifier instead. Flattened like the barcodes so the
    // form can edit it, and written back with its marketplace attached.
    ebayItemId: idents.find((i) => i.type === 'MARKETPLACE_ID'
      && String(i.marketplace || '').toUpperCase() === 'EBAY')?.originalValue || '',
    color: attr.color || '',
    size: attr.size || '',
    material: attr.material || '',
    model: attr.model || '',
    variant: attr.variant || '',
    country: attr.country || '',
    title: p.title,
    brand: p.brand,
    description: p.description,
    category: p.category,
    status: p.status,
    condition: p.condition,
    // Physical attributes — needed so the edit form prefills instead of
    // silently blanking them on the next save.
    packQuantity: p.packQuantity,
    weight: p.weight,
    height: p.height,
    width: p.width,
    length: p.length,
    // Our current selling price — the "Current price" KPI and the default
    // anchor for GET /products/{id}/profitability (called with no price param).
    ourPrice: p.ourPrice,
    // Real first-image URL for the form. Distinct from `image` below, which
    // falls back to a placehold.co URL for display and must never be saved.
    imageUrl: p.images?.[0]?.url || p.images?.[0]?.imageUrl || '',
    // Price/margin are not part of Phase 1 product data — leave undefined so
    // formatCurrency() renders '—' rather than a fabricated value.
    price: p.price,
    margin: p.margin,
    marketplaces,
    identifiers: idents,
    image: p.images?.[0]?.url || p.images?.[0]?.imageUrl || 'https://placehold.co/80x80?text=—',
    images: p.images || [],
    attributes: p.attributes || [],
  }
}

// Product form values -> CreateProductRequest / UpdateProductRequest.
//
// The form is flat; the API is not. Per the OpenAPI spec:
//   - ASIN is not a column — it is an entry in identifiers[] ({type, originalValue}).
//   - There is no price, margin or marketplace field on either request.
//   - sku is required on create and ABSENT from UpdateProductRequest (immutable).
// Without this reshaping the extra keys are dropped by Jackson and the ASIN the
// user typed never reaches the database.
const ID_TYPES = ['ASIN', 'UPC', 'EAN', 'GTIN', 'MPN', 'MARKETPLACE_ID']
// Flat form fields that are really identifiers[] entries. Add a row here and
// the field round-trips through both mapProduct() and toProductRequest().
const FORM_ID_TYPES = { asin: 'ASIN', ean: 'EAN', upc: 'UPC', gtin: 'GTIN', mpn: 'MPN' }
// Variant attribute fields the form owns (ProductAttributeRequest, FR-PROD-003).
const ATTR_FIELDS = ['color', 'size', 'material', 'model', 'variant', 'country']
const clean = (s) => {
  const t = typeof s === 'string' ? s.trim() : s
  return t === '' || t == null ? undefined : t
}
// An untouched <input type="number"> yields '' — send undefined, not '' or NaN,
// or the backend rejects the numeric field.
const num = (n) => {
  if (n === '' || n == null) return undefined
  const v = Number(n)
  return Number.isFinite(v) ? v : undefined
}

export function toProductRequest(v, { create = false } = {}) {
  // Rebuild identifiers: what the form manages wins, other existing types are
  // carried over. mapProduct() returns responses, so fall back to normalizedValue.
  const identifiers = []
  for (const [field, type] of Object.entries(FORM_ID_TYPES)) {
    if (clean(v[field])) identifiers.push({ type, originalValue: clean(v[field]) })
  }
  if (clean(v.ebayItemId)) {
    identifiers.push({ type: 'MARKETPLACE_ID', originalValue: clean(v.ebayItemId), marketplace: 'EBAY' })
  }
  const managed = new Set(Object.values(FORM_ID_TYPES))
  for (const i of v.identifiers || []) {
    const original = clean(i.originalValue) || clean(i.normalizedValue)
    if (!original || managed.has(i.type) || !ID_TYPES.includes(i.type)) continue
    // The eBay item number is managed above; carrying the existing row through
    // as well would send the same identifier twice.
    if (i.type === 'MARKETPLACE_ID' && String(i.marketplace || '').toUpperCase() === 'EBAY'
      && clean(v.ebayItemId)) continue
    identifiers.push({ type: i.type, originalValue: original, marketplace: clean(i.marketplace) })
  }

  // The Image URL field owns images[0]; any further images the product already
  // has are carried through untouched. Placeholder URLs invented by
  // mapProduct() are never written back. NOTE: clearing the field sends no
  // `images` key at all (see the length guard below), so it does not delete an
  // existing image — that needs a backend clear/delete semantic we don't have.
  const images = []
  if (clean(v.imageUrl)) images.push({ url: clean(v.imageUrl) })
  for (const i of (v.images || []).slice(1)) {
    const url = clean(i.url) || clean(i.imageUrl)
    if (url && !/placehold/i.test(url)) images.push({ url, hash: clean(i.hash), status: clean(i.status) })
  }

  // The form owns attributes[0] (flattened by mapProduct); any further rows the
  // product already has are carried through. Omitted entirely when all six are
  // blank, so an untouched product is not sent an empty attribute row.
  const attributes = []
  const first = {}
  for (const f of ATTR_FIELDS) if (clean(v[f])) first[f] = clean(v[f])
  if (Object.keys(first).length) attributes.push(first)
  for (const a of (v.attributes || []).slice(1)) {
    const row = {}
    for (const f of ATTR_FIELDS) if (clean(a[f])) row[f] = clean(a[f])
    if (Object.keys(row).length) attributes.push(row)
  }

  const req = {
    title: clean(v.title),
    brand: clean(v.brand),
    description: clean(v.description),
    category: clean(v.category),
    status: clean(v.status),
    condition: clean(v.condition),
    ourPrice: num(v.ourPrice),
    packQuantity: num(v.packQuantity),
    weight: num(v.weight),
    height: num(v.height),
    width: num(v.width),
    length: num(v.length),
  }
  if (identifiers.length) req.identifiers = identifiers
  if (images.length) req.images = images
  if (attributes.length) req.attributes = attributes
  if (create) req.sku = clean(v.sku)   // create-only: SKU cannot be updated

  // Drop undefined so PUT does not blank out fields the form never touched.
  return Object.fromEntries(Object.entries(req).filter(([, val]) => val !== undefined))
}

// PagedResponse<T> passthrough (content already the same keys the UI uses).
export function mapPage(page, itemMapper = (x) => x) {
  return {
    content: (page.content || []).map(itemMapper),
    totalElements: page.totalElements ?? 0,
    totalPages: page.totalPages ?? 1,
    page: page.page ?? 1,
    size: page.size ?? 10,
  }
}

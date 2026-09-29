// Single source of truth for backend routes (baseURL already includes /api/v1).
// Mirrors the live springdoc contract at http://192.168.10.253:8080/v3/api-docs
export const ENDPOINTS = {
  // ── LIVE (Phase 1) ────────────────────────────────────────────
  auth: {
    login: '/auth/login',
    logout: '/auth/logout',
    refresh: '/auth/refresh',
    me: '/auth/me',
    changePassword: '/auth/change-password',
    // Self-service password reset (no auth). Three steps, all keyed on email+code.
    forgotPassword: '/auth/forgot-password',      // POST { email } -> emails a 6-digit code
    verifyResetCode: '/auth/verify-reset-code',   // POST { email, code }
    resetPassword: '/auth/reset-password',        // POST { email, code, newPassword }
    // Two-factor authentication (backend TBD — update paths when ready).
    twoFactor: {
      status: '/auth/2fa/status',
      setup: '/auth/2fa/setup',
      enable: '/auth/2fa/enable',
      disable: '/auth/2fa/disable',
      verify: '/auth/2fa/verify',
      resend: '/auth/2fa/resend',
    },
  },
  // Self-service profile for the signed-in user. Narrower than /users/{id} by
  // design — it cannot touch accessType, so it is not a privilege-escalation
  // path. Password changes go through /auth/change-password.
  profile: '/profile',                                // GET | PUT
  users: {
    list: '/users',
    byId: (id) => `/users/${id}`,
    roles: (id) => `/users/${id}/roles`,
    activate: (id) => `/users/${id}/activate`,
    deactivate: (id) => `/users/${id}/deactivate`,
    resetPassword: (id) => `/users/${id}/reset-password`,
  },
  // Roles master data. GET is live today; write ops are the contract the
  // backend should implement (POST /roles, PUT/DELETE /roles/{name}).
  roles: {
    list: '/roles',
    create: '/roles',
    byName: (name) => `/roles/${name}`,
  },
  // Slack / Teams delivery for alerts. Webhook URLs are write-only —
  // reads return a masked value plus a `configured` flag.
  notificationChannels: {
    root: '/notification-channels',        // GET | PUT
    test: '/notification-channels/test',   // POST { channel }
  },

  // Scheduled competitor discovery. One monitor per product; the scheduler
  // runs the same search path with the monitor's interval as the freshness
  // bound, so a monitor never reports data older than its own cadence.
  monitors: {
    list: '/monitors',                 // GET ?productId&enabled | POST
    byId: (id) => `/monitors/${id}`,   // PATCH | DELETE
  },

  products: {
    list: '/products',
    // Distinct brands/categories/statuses across the tenant's whole catalog,
    // for the list filters. One cheap call, no sampling.
    filterOptions: '/products/filter-options',
    // CSV of the filtered catalog. Returns a file, not JSON — see
    // productService.exportCsv for why it cannot be a plain <a href>.
    export: '/products/export',
    // Perceptual-hash lookup over the tenant's own product images.
    // POST { image: "data:image/…;base64,…" } -> ranked matches
    searchByImage: '/products/search-by-image',
    byId: (id) => `/products/${id}`,
    // POST, no body — proposes real Amazon ASINs for a product known only
    // by name. Suggestions only; the user picks and still saves.
    suggestAsin: (id) => `/products/${id}/suggest-asin`,
    imports: '/products/imports',
    importPreview: '/products/imports/preview',
    importById: (id) => `/products/imports/${id}`,
    importErrors: (id) => `/products/imports/${id}/errors`,
  },
  search: {
    run: '/search',
    history: '/search/history',
  },
  // Bell-icon notifications. Scoped to the caller by the JWT — no userId param.
  notifications: {
    list: '/notifications',                            // ?page=0&size=20 (0-based, size max 100)
    unreadCount: '/notifications/unread-count',        // -> { unread }
    markRead: (id) => `/notifications/${id}/read`,     // PATCH
    markAllRead: '/notifications/read-all',            // PATCH -> { updated }
  },
  marketplaces: {
    list: '/marketplaces',
    health: '/marketplaces/health',
    mpHealth: (mp) => `/marketplaces/${mp}/health`,
    search: (mp) => `/marketplaces/${mp}/search`,
    // FRD §4.3 in one call: the backend runs the whole waterfall — find
    // (steps 1-5), judge (step 6) and rules (step 7) — and returns one result
    // set, the verdicts, and a step-by-step trace under one correlation ID.
    // `judge: true` turns steps 6-7 on. There is no separate match endpoint.
    channelSearch: '/marketplaces/search',        // POST ChannelSearchRequest
    parseUrl: '/marketplaces/parse-url',          // POST ?url= -> { itemId, countryCode, … }
    // POST ?query=&identifierType= — the same ASIN lookup, but from anything
    // typed rather than from a saved product.
    findAsin: '/marketplaces/find-asin',
    fees: (mp) => `/marketplaces/${mp}/fees`,
    listing: (mp, itemId) => `/marketplaces/${mp}/listings/${itemId}`,
    listingPrice: (mp, itemId) => `/marketplaces/${mp}/listings/${itemId}/price`,
    // Competing sellers on ONE listing — not other products. An ASIN is a
    // single catalogue item, so a search by ASIN can only ever return one
    // result; the several "competitors" visible on an Amazon page are several
    // merchants offering that same item, and they live here.
    listingOffers: (mp, itemId) => `/marketplaces/${mp}/listings/${itemId}/offers`,
    // eBay's answer to the offers panel. eBay has no Buy Box and no shared
    // product page, so a listing's competitors are the OTHER listings of the
    // same product, found by its barcode or eBay product id.
    otherSellers: (mp, itemId) => `/marketplaces/${mp}/listings/${itemId}/other-sellers`,
    listingSales: (mp, itemId) => `/marketplaces/${mp}/listings/${itemId}/sales`,
    // Ingestion + raw source records
    amazonIngest: (asin) => `/marketplaces/amazon/products/${asin}/ingest`,
    amazonRaw: (asin) => `/marketplaces/amazon/products/${asin}/raw`,
    ebayIngest: (itemId) => `/marketplaces/ebay/items/${itemId}/ingest`,
    ebayRaw: (itemId) => `/marketplaces/ebay/items/${itemId}/raw`,
  },
  ai: {
    match: '/ai/match',
    executions: '/ai/executions',
  },
  // Platform admin — manage tenant companies (clients) + subscriptions.
  adminClients: {
    list: '/admin/clients',
    byId: (id) => `/admin/clients/${id}`,
    activate: (id) => `/admin/clients/${id}/activate`,
    deactivate: (id) => `/admin/clients/${id}/deactivate`,
    suspend: (id) => `/admin/clients/${id}/suspend`,
    resume: (id) => `/admin/clients/${id}/resume`,
    stats: (id) => `/admin/clients/${id}/stats`,
    usage: (id) => `/admin/clients/${id}/usage`,
    payments: (id) => `/admin/clients/${id}/payments`,   // GET ?page&size — this client's payments
    resendActivation: (id) => `/admin/clients/${id}/resend-activation`,
    sub: {
      assign: (t) => `/admin/clients/${t}/subscription/assign`,
      upgrade: (t) => `/admin/clients/${t}/subscription/upgrade`,
      downgrade: (t) => `/admin/clients/${t}/subscription/downgrade`,
      extend: (t) => `/admin/clients/${t}/subscription/extend`,
      renew: (t) => `/admin/clients/${t}/subscription/renew`,
      cancel: (t) => `/admin/clients/${t}/subscription/cancel`,
      status: (t) => `/admin/clients/${t}/subscription/status`,
      history: (t) => `/admin/clients/${t}/subscription/history`,
    },
  },
  // Platform admin — subscription plan catalog.
  adminPlans: {
    list: '/admin/plans',
    create: '/admin/plans',
    byId: (id) => `/admin/plans/${id}`,
  },
  // Platform admin — payment records (Stripe). Paged.
  adminPayments: {
    list: '/admin/payments',   // GET ?page&size — all clients' payments
  },
  // Platform admin — marketplace integration config (LIVE). Contract:
  //   GET  /admin/marketplaces          -> [{ code, name, description, enabled, configured,
  //                                            status, lastChecked, lastMessage, capabilities[], credentials{} }]
  //   PUT  /admin/marketplaces/{code}    { enabled, credentials{...} }  -> updated entry (secrets masked)
  //   POST /admin/marketplaces/{code}/test -> updated entry (status, lastChecked, lastMessage)
  // Secrets are write-only: GET/PUT responses mask them (••••1234); send a secret
  // only when the admin types a new one (omit/blank keeps the stored value).
  adminMarketplaces: {
    list: '/admin/marketplaces',
    byCode: (code) => `/admin/marketplaces/${code}`,
    test: (code) => `/admin/marketplaces/${code}/test`,
  },
  // Platform admin — email templates (LIVE). Contract:
  //   GET  /admin/email-templates             -> [{ key, name, description, subject, body, edited, variables[] }]
  //   GET  /admin/email-templates/{key}         one template (same shape)
  //   PUT  /admin/email-templates/{key}        { subject, body }  -> updated template (edited:true)
  //   GET  /admin/email-templates/{key}/preview -> { subject, htmlBody } rendered with sample data
  //   POST /admin/email-templates/{key}/test   { toEmail }
  adminEmailTemplates: {
    list: '/admin/email-templates',
    byKey: (key) => `/admin/email-templates/${key}`,
    preview: (key) => `/admin/email-templates/${key}/preview`,
    test: (key) => `/admin/email-templates/${key}/test`,
  },
  // Dashboard aggregates, computed per request for the logged-in company.
  dashboard: {
    summary: '/dashboard/summary',
    charts: '/dashboard/charts',
    activities: '/dashboard/activities',
    // CSV behind each chart's download icon. `chart` is the kebab-case name
    // of the series, e.g. price-trend.
    chartExport: (chart) => `/dashboard/charts/${chart}/export`,
    summaryExport: '/dashboard/summary/export',
  },
  // CSV for one listing's recorded prices, and for a product's market spread.
  priceHistoryExport: {
    listing: (id) => `/listings/${id}/price-history/export`,
    productRollup: (id) => `/products/${id}/price-history-rollup/export`,
  },
  // Tenant self-service (the logged-in company managing its OWN account).
  client: {
    company: '/client/company',
    dashboard: '/client/dashboard',
    subscription: '/client/subscription',
    plans: '/client/plans',                                   // GET — plans the client can choose
    subUpgrade: '/client/subscription/upgrade',              // POST ?planCode= (empty body)
    subDowngrade: '/client/subscription/downgrade',          // POST ?planCode= (empty body)
    // POST ?billingCycle= (optional). Renews the CURRENT plan — no plan code.
    subRenew: '/client/subscription/renew',
    payments: '/client/payments',                            // GET ?page&size
  },
  // Public (no auth) — marketing site content + lead capture.
  publicSite: {
    pricing: '/public/pricing',
    plans: '/public/subscription-plans',
    faqs: '/public/faqs',
    features: '/public/features',
    testimonials: '/public/testimonials',
    requestDemo: '/public/request-demo',
    contact: '/public/contact-us',
    newsletter: '/public/newsletter',
    registration: '/public/client-registration',
    subscription: '/public/subscription',
  },
  // Public self-signup — a company registering itself (no admin invitation).
  register: {
    start: '/public/register',                 // POST { planCode, billingCycle, ...profile } -> { token, nextStep }
    verifyCode: '/public/register/verify-code',  // POST { token, code }
    setPassword: '/public/register/set-password',// POST { token, code, password }
    checkout: '/public/register/checkout',        // POST { token, successUrl, cancelUrl } -> { checkoutUrl, amount }
    mockConfirm: '/public/register/mock-confirm',  // POST { token } -> { completed, loginUrl, ... }
  },
  // Client onboarding via secure invitation link — matches the live contract.
  onboarding: {
    validate: '/public/onboarding/validate',            // POST { token }
    verifyCode: '/public/onboarding/verify-code',        // POST { token, code }
    setPassword: '/public/onboarding/set-password',      // POST { token, code, password }
    completeProfile: '/public/onboarding/complete-profile', // POST { token, ...profile }
    choosePlan: '/public/onboarding/choose-plan',        // POST { token, planCode, billingCycle }
    checkout: '/public/payment/checkout',                // POST { token, successUrl, cancelUrl } -> { checkoutUrl }
    mockConfirm: '/public/payment/mock-confirm',         // POST (mock provider) -> ActivationCompleteResponse
  },

  // ── Product intelligence (per-product) ────────────────────────
  // Competitor discovery through to price recommendations. Everything
  // competitor-derived requires a search job to have run for the product first;
  // before that, candidates/market-prices come back empty.
  productIntel: {
    searchJobs: (id) => `/products/${id}/search-jobs`,        // POST { markets[], maxResults }
    searchJob: (jobId) => `/search-jobs/${jobId}`,            // GET  progress poll
    marketPrices: (id) => `/products/${id}/market-prices`,    // GET  ?matchedOnly
    candidates: (id) => `/products/${id}/candidates`,
    // POST ?marketplace=&marketplaceItemId= — attaches a marketplace listing
    // to this product as a CANDIDATE. Query params, null body.
    attachListing: (id) => `/products/${id}/competitor-listings`,
    priceHistory: (listingId) => `/listings/${listingId}/price-history`, // GET ?from&to (ISO)
    // Per-product trend across all its listings. GET ?bucket=day|week&from&to
    priceHistoryRollup: (id) => `/products/${id}/price-history-rollup`,
    review: (listingId) => `/candidates/${listingId}/review`, // POST { decision }
    // POST ?limit= — judges UNJUDGED candidates and returns the full list with
    // verdicts attached. Metered: only ever call this from an explicit action.
    aiReview: (id) => `/products/${id}/ai-review`,
    // GET ?asOf=YYYY-MM-DD selects the version in force on that date; PUT
    // writes the version named by validFrom. FR-COST-001's effective dates.
    costProfile: (id) => `/products/${id}/cost-profile`,      // GET ?asOf | PUT
    costProfileHistory: (id) => `/products/${id}/cost-profile/history`,
    profitability: (id) => `/products/${id}/profitability`,   // GET ?price= ?asOf= (both optional)
    // Stored daily snapshots — separate from the live calculation above.
    // GET ?from&to as YYYY-MM-DD (date, not datetime).
    profitabilityTrend: (id) => `/products/${id}/profitability/trend`,
    sales: (id) => `/products/${id}/sales`,
    recommendations: (id) => `/products/${id}/price-recommendations`, // GET list | POST generate
    // DRAFT -> submit -> PENDING_APPROVAL -> approve -> publish. Submitting
    // separates 'I am done drafting' from 'I authorise this price'.
    submit: (recId) => `/recommendations/${recId}/submit`,
    approve: (recId) => `/recommendations/${recId}/approve`,
    publish: (recId) => `/recommendations/${recId}/publish`,
    reject: (recId) => `/recommendations/${recId}/reject`,
    // Undoes a published price. The record ends REJECTED with rolledBackAt set
    // rather than returning to draft — an audit has to keep the fact that it
    // went live, and a draft would erase it.
    rollback: (recId) => `/recommendations/${recId}/rollback`,
  },
  alertRules: {
    list: '/alerts/rules',                          // GET ?productId= (optional) | POST
    byId: (ruleId) => `/alerts/rules/${ruleId}`,    // DELETE
    evaluate: '/alerts/rules/evaluate',             // POST -> { fired }
    // One rule across many products. Body is BulkAlertRuleRequest; the reply
    // reports an outcome per product rather than one overall success, because
    // a duplicate and a missing product are not failures and must not read as
    // one. Capped at 500 ids per call.
    bulk: '/alerts/rules/bulk',
    // Both take a bare array of rule ids as the body, not an object.
    bulkDelete: '/alerts/rules/bulk-delete',        // POST [ids] -> { deleted }
    bulkActive: '/alerts/rules/bulk-active',        // POST ?active= [ids] -> { changed }
    setActive: (ruleId) => `/alerts/rules/${ruleId}/active`,  // PATCH ?active=
  },
  // Tenant-wide competitor listing browser — reads what search jobs already
  // stored; it does not call marketplaces itself.
  // GET ?page&size&search&marketplace&condition&matchStatus (page is 0-based).
  competitorListings: '/competitor-listings',

  // The bounds automatic repricing runs inside. Without one, nothing limits a
  // recommendation — acceptable while a person reads it on screen, not once it
  // can publish itself. FR-REC-002's "bounded automation".
  pricingPolicies: {
    root: '/pricing-policies',                    // GET list | POST create-or-update
    byId: (id) => `/pricing-policies/${id}`,      // DELETE
    // What actually applies to one product on one channel, after the
    // product+channel -> product -> company+channel -> company fallback.
    effective: '/pricing-policies/effective',     // GET ?productId&marketplace
  },

  // FR-PROFIT-001's "stored FX rate". Until these exist, statistics across
  // mixed currencies are arithmetic on incompatible numbers.
  fxRates: {
    root: '/fx-rates',                            // GET list | POST upsert
    byId: (id) => `/fx-rates/${id}`,              // DELETE
    reportingCurrency: '/fx-rates/reporting-currency',
    convert: '/fx-rates/convert',                 // GET ?amount&from&to
  },

  // Bulk product search — the same spreadsheet the product import takes, but
  // it searches the marketplaces and saves NOTHING. /products/imports is the
  // opposite: it creates products and never calls Amazon. One file, two
  // deliberately different destinations.
  bulkSearch: {
    root: '/search/bulk',                                    // POST multipart ?judge&country&postalCode | GET ?page&size
    job: (id) => `/search/bulk/${id}`,
    rows: (id) => `/search/bulk/${id}/rows`,                 // GET ?status&page&size
    save: (jobId, rowId) => `/search/bulk/${jobId}/rows/${rowId}/save`,  // POST ?sku
  },

  // Items the AI judged to be real products during a search, followed over
  // time. Not tenant-scoped: an ASIN's price is a public fact, so every client
  // watching it reads one history rather than a private fragment of one.
  marketplaceItems: {
    list: '/marketplace-items',                                    // GET ?marketplace&search&page&size
    detail: (mp, id) => `/marketplace-items/${mp}/${id}`,
    priceHistory: (mp, id) => `/marketplace-items/${mp}/${id}/price-history`,  // GET ?from&to — plain array
    itemChanges: (mp, id) => `/marketplace-items/${mp}/${id}/changes`,         // GET ?page&size
    changes: '/marketplace-items/changes',                         // GET ?marketplace&field&from&page&size
  },
  // Pending-only, pre-scored review queue across every product (FR-MATCH-003).
  // GET ?productId&sort=score|lowest|recent&page&size
  matchReview: '/match-review',

  // AI analyst — deterministic Q&A over stored costs, prices and listings.
  // Single request/response, no streaming.
  aiAnalyst: {
    suggestions: '/ai/analyst/suggestions',   // GET  -> string[]
    ask: '/ai/analyst/ask',                   // POST { question } (max 500)
  },

  // Competitor-price export across every product, filtered by marketplace,
  // category and date range.
  reports: {
    generate: '/reports/generate',   // POST -> preview rows + full file content
    download: '/reports/download',   // GET  ?format&marketplace&category&from&to
  },

  // Outbound email (SMTP). When enabled, the tenant's activation and welcome
  // emails send from their own server; when off, the platform default is used.
  emailConfig: {
    get: '/settings/email-config',              // GET | PUT
    test: '/settings/email-config/test',        // POST -> { status, lastMessage }
  },

  // Tenant-wide cost defaults (FR-COST-001 "global defaults"). Distinct from
  // productIntel.costProfile, which is the per-product override.
  costModel: '/cost-model',                       // GET | PUT

  // Tenant-wide recommendation queue. The per-recommendation actions live on
  // productIntel (approve / reject / publish) — same URLs, defined once.
  recommendations: {
    list: '/recommendations',                     // GET ?status&page&size
    generate: '/recommendations/generate',        // POST -> { generated: n }
  },

  // ── NOT YET IN BACKEND (still served by mock services) ────────
  // dashboard, competitors (aggregate), reports, ai chat
}

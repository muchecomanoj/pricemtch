// Credential FIELD SCHEMAS per marketplace — which inputs the Configure modal
// renders, their labels, and (critically) the exact `credentials` keys the
// backend expects. The live GET /admin/marketplaces supplies the data (status,
// capabilities, masked secret values); the frontend owns only this form schema.
//
// Secret fields are write-only: the list returns them masked (••••1234) which we
// show as the placeholder; the field starts empty and is sent only when typed.

export const MARKETPLACE_CONFIG = {
  AMAZON: {
    docsHint: 'Amazon SP-API — create an app in Seller Central, then generate Login-with-Amazon (LWA) credentials. Requires the right roles and selling-partner authorization.',
    fields: [
      { key: 'sellerToken', label: 'Seller / Merchant token', type: 'password', secret: true, required: true },
      { key: 'lwaClientId', label: 'LWA Client ID', type: 'text', required: true },
      { key: 'lwaClientSecret', label: 'LWA Client Secret', type: 'password', secret: true, required: true },
      { key: 'lwaRefreshToken', label: 'LWA Refresh Token', type: 'password', secret: true, required: true },
      { key: 'region', label: 'Region', type: 'select', options: ['NA', 'EU', 'FE'], required: true },
      { key: 'marketplaceId', label: 'Marketplace ID (optional)', type: 'text' },
    ],
  },
  EBAY: {
    docsHint: 'eBay Buy Browse API — create an application in the eBay Developer Program.',
    // Sold-history (Marketplace Insights) is restricted/limited access — flag it.
    notes: [{ tone: 'warning', text: 'Marketplace Insights (sold-history) is restricted and not open to all developers — the platform does not assume this access.' }],
    fields: [
      { key: 'appId', label: 'App ID (Client ID)', type: 'text', required: true },
      { key: 'certId', label: 'Cert ID (Client Secret)', type: 'password', secret: true, required: true },
      { key: 'devId', label: 'Dev ID', type: 'text' },
      { key: 'environment', label: 'Environment', type: 'select', options: ['PRODUCTION', 'SANDBOX'], required: true },
      { key: 'marketplace', label: 'Marketplace', type: 'select', options: ['EBAY_US', 'EBAY_GB', 'EBAY_DE', 'EBAY_AU', 'EBAY_CA'] },
    ],
  },
  KEEPA: {
    docsHint: 'Keepa — an Amazon data source that needs only an API key (no seller authorization). Get a key from keepa.com.',
    fields: [
      { key: 'apiKey', label: 'API Key', type: 'password', secret: true, required: true },
      { key: 'domain', label: 'Domain', type: 'select', required: true,
        options: ['1', '2', '3', '4', '5', '6', '8', '9', '10', '11'],
        // Human-readable hints for the numeric Keepa domain ids.
        optionLabels: { 1: '1 — US', 2: '2 — UK', 3: '3 — DE', 4: '4 — FR', 5: '5 — JP', 6: '6 — CA', 8: '8 — IT', 9: '9 — ES', 10: '10 — IN', 11: '11 — MX' } },
    ],
  },
  // Open Web has no credentials — rendered as a static "Connected (mock)" card.
  WEB: { fields: [], static: true },
}

// Fallback schema for any marketplace code the backend adds later.
export const DEFAULT_CONFIG = { docsHint: '', fields: [] }

export const configSchema = (code) => MARKETPLACE_CONFIG[code] || DEFAULT_CONFIG

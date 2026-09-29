import { FaAmazon } from 'react-icons/fa'
import { mpLabel, storefrontLabel } from '../../utils/marketplaces'

// Which marketplace a listing is from, recognisable at a glance.
//
// Amazon and eBay results sit in the same grid, and a grey "Amazon" in the
// small print was the only thing telling them apart. The brand marks are what
// people already recognise, so the eye finds them before it reads anything.
// eBay is its four-colour wordmark rather than a monochrome icon — the colours
// are the recognisable part. Anything else falls back to its name.
export default function MarketplaceLogo({ marketplace, storefront, className = '' }) {
  const code = String(marketplace || '').toUpperCase()
  // The same ASIN on amazon.com and amazon.ca is two listings at two prices in
  // two currencies. Without the country on the badge they read as a duplicate.
  const cc = storefront ? String(storefront).toUpperCase() : null
  const place = cc ? <span className="mp-logo__cc" title={storefrontLabel({ countryCode: cc }) || cc}>{cc}</span> : null

  if (code === 'AMAZON') {
    return (
      <span className={`mp-logo mp-logo--amazon ${className}`} title="Amazon" aria-label="Amazon">
        <FaAmazon size={13} aria-hidden="true" />
        <span>amazon</span>
        {place}
      </span>
    )
  }

  if (code === 'EBAY') {
    return (
      <span className={`mp-logo mp-logo--ebay ${className}`} title="eBay" aria-label="eBay">
        <span aria-hidden="true">
          <b className="e">e</b><b className="b">b</b><b className="a">a</b><b className="y">y</b>
        </span>
        {place}
      </span>
    )
  }

  return <span className={`mp-logo ${className}`}>{mpLabel(code)}{place}</span>
}

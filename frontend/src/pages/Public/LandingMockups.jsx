import { FiTrendingUp, FiZap } from 'react-icons/fi'

// Static illustrations of the product: they are drawn in code, not content.

// ── Hero mockup: dashboard preview card ──────────────────────────────────────
export function HeroDashboard() {
  return (
    <div className="lp-mock lp-mock-dash">
      <div className="lp-mock-bar">
        <span className="lp-tl r" /><span className="lp-tl y" /><span className="lp-tl g" />
        <span className="lp-mock-title">Dashboard · Maharana Retail</span>
      </div>
      <div className="lp-mock-body">
        <div className="lp-mini-row">
          <div className="lp-mini-stat"><span>Total Products</span><strong>1,284</strong><em className="up">▲ 4.2%</em></div>
          <div className="lp-mini-stat"><span>Avg Margin</span><strong>27.4%</strong><em className="up">▲ 0.9%</em></div>
        </div>
        <div className="lp-mini-chart">
          <div className="lp-mini-chart-head"><span>Price Trend — Ours vs Market</span><span className="lp-muted">7 mo</span></div>
          <TrendChart />
        </div>
        <div className="lp-mini-alert">
          <span className="lp-mini-alert-icon"><FiTrendingUp /></span>
          <div>
            <div className="lp-mini-alert-title">AI move: Echo Dot (5th Gen)</div>
            <div className="lp-muted sm">$44.99 → $42.49 · 91% confidence</div>
          </div>
          <span className="lp-chip green">Low risk</span>
        </div>
      </div>
      <div className="lp-float-alert">
        <div className="lp-muted sm">Competitor alert</div>
        <div className="lp-fw">SellerX dropped 6%</div>
      </div>
    </div>
  )
}

export function TrendChart() {
  return (
    <svg viewBox="0 0 360 120" className="lp-trend" preserveAspectRatio="none">
      <polyline className="lp-trend-market" points="0,86 60,80 120,74 180,60 240,52 300,40 360,30" />
      <polyline className="lp-trend-ours" points="0,96 60,92 120,88 180,78 240,72 300,58 360,50" />
    </svg>
  )
}

export function ProductsTable() {
  const rows = [
    ['ED', 'Echo Dot (5th Gen)', '$29.99', '15.0%', 'Matched', 'green'],
    ['FT', 'Fire TV Stick 4K', '$30.99', '16.0%', 'Review', 'amber'],
    ['KP', 'Kindle Paperwhite', '$31.99', '17.0%', 'Unmatched', 'grey'],
    ['WH', 'Sony WH-1000XM4', '$32.99', '18.0%', 'Matched', 'green'],
  ]
  return (
    <div className="lp-mock">
      <div className="lp-mock-bar lp-mock-bar-plain"><span className="lp-mock-title">Products · 1,284 tracked</span></div>
      <table className="lp-ptable">
        <thead><tr><th>PRODUCT</th><th>PRICE</th><th>MARGIN</th><th>STATUS</th></tr></thead>
        <tbody>
          {rows.map(([ini, name, price, margin, status, tone]) => (
            <tr key={name}>
              <td><span className={`lp-ini t-${tone}`}>{ini}</span>{name}</td>
              <td>{price}</td>
              <td className="lp-muted">{margin}</td>
              <td><span className={`lp-chip ${tone}`}>{status}</span></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

// ── AI recommendation mockup ────────────────────────────────────────────────
export function AIRecommendation() {
  return (
    <div className="lp-mock lp-ai-card">
      <div className="lp-mock-bar">
        <span className="lp-tl r" /><span className="lp-tl y" /><span className="lp-tl g" />
        <span className="lp-mock-title">AI Recommendation · Echo Dot (5th Gen)</span>
      </div>
      <div className="lp-mock-body">
        <div className="lp-ai-head">
          <div>
            <div className="lp-muted sm">Recommended price</div>
            <div className="lp-ai-price">$42.49</div>
          </div>
          <span className="lp-chip green">91% confidence</span>
        </div>
        <div className="lp-ai-rows">
          <div><span>Current price</span><strong>$44.99</strong></div>
          <div><span>Competitor average</span><strong>$43.80</strong></div>
          <div><span>Expected margin</span><strong className="up">+3.5%</strong></div>
        </div>
        <div className="lp-ai-reason">
          <span className="lp-mini-alert-icon"><FiZap /></span>
          Increase price by 3.5% while staying within the competitive range and improving projected margin.
        </div>
      </div>
    </div>
  )
}

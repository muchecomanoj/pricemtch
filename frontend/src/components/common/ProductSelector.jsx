import { useEffect, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import ComboInput from '../forms/ComboInput'
import { productService } from '../../services/productService'

// Shared product picker for the per-product intelligence pages.
//
// A ComboInput rather than a <select>: the list is data-driven and can run to
// 100 products, so it needs searching. Short fixed lists (status, condition,
// format) stay native selects — see the note on SearchBar.
export default function ProductSelector({ value, onChange, label = 'Product', width = 280 }) {
  const { data, isLoading } = useQuery({
    queryKey: ['products', 'selector'],
    queryFn: () => productService.list({ page: 1, size: 100 }),
  })
  const products = useMemo(() => data?.content ?? [], [data])

  const labelFor = (p) => (p.sku ? `${p.title} — ${p.sku}` : p.title)
  const byLabel = useMemo(() => new Map(products.map((p) => [labelFor(p), p.id])), [products])
  const currentLabel = useMemo(() => {
    const match = products.find((p) => p.id === value)
    return match ? labelFor(match) : ''
  }, [products, value])

  // Auto-select the first product so the page has something to render on mount.
  useEffect(() => {
    if (!value && products.length) onChange(products[0].id)
  }, [products, value, onChange])

  return (
    <div style={{ width }}>
      <label className="form-label" htmlFor="product-selector">{label}</label>
      <ComboInput
        id="product-selector"
        value={currentLabel}
        onChange={(v) => onChange(byLabel.get(v) ?? null)}
        options={[...byLabel.keys()]}
        placeholder={isLoading ? 'Loading products…' : 'Choose a product'}
        disabled={isLoading || !products.length}
        strict
      />
    </div>
  )
}

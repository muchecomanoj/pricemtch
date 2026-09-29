import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import MasterDataPage from './MasterDataPage'
import { brandStore } from '../../services/localMasterData'
import { productService } from '../../services/productService'

// Seeded once from the brands present in the catalog; user edits win after that.
export default function Brands() {
  const { data } = useQuery({
    queryKey: ['products', 'facets'],
    queryFn: () => productService.list({ page: 1, size: 100 }),
  })

  const usage = useMemo(() => {
    const counts = new Map()
    for (const p of data?.content ?? []) {
      if (p.brand) counts.set(p.brand, (counts.get(p.brand) || 0) + 1)
    }
    return counts
  }, [data])

  const seed = useMemo(
    () => [...usage.keys()].sort().map((name) => ({ name, website: '' })),
    [usage],
  )

  return (
    <MasterDataPage
      title="Brands"
      entity="brand"
      storeKey="brands"
      store={brandStore}
      seed={seed}
      description="Brands represented in your catalog."
      fields={[
        { key: 'name', label: 'Brand name', required: true, placeholder: 'e.g. Ninja' },
        { key: 'website', label: 'Website', type: 'url', placeholder: 'https://…' },
      ]}
      columns={[
        { key: 'name', header: 'Brand', render: (r) => <span className="fw-semibold">{r.name}</span> },
        {
          key: 'website',
          header: 'Website',
          render: (r) => (r.website
            ? <a href={r.website} target="_blank" rel="noreferrer" className="small">{r.website}</a>
            : <span className="text-muted">—</span>),
        },
        {
          key: 'usage',
          header: 'Products',
          render: (r) => (usage.get(r.name)
            ? <span className="mr-pill mr-pill--blue">{usage.get(r.name)}</span>
            : <span className="text-muted small">Unused</span>),
        },
      ]}
    />
  )
}

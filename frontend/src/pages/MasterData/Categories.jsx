import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import MasterDataPage from './MasterDataPage'
import { categoryStore } from '../../services/localMasterData'
import { productService } from '../../services/productService'

// Seeded from the categories actually in use in the catalog, so the list starts
// meaningful instead of empty. After that it is user-owned — the seed is never
// reapplied over their edits.
export default function Categories() {
  const { data } = useQuery({
    queryKey: ['products', 'facets'],
    queryFn: () => productService.list({ page: 1, size: 100 }),
  })

  const seed = useMemo(() => {
    const products = data?.content ?? []
    const counts = new Map()
    for (const p of products) {
      if (p.category) counts.set(p.category, (counts.get(p.category) || 0) + 1)
    }
    return [...counts.keys()].sort().map((name) => ({ name, description: '' }))
  }, [data])

  const usage = useMemo(() => {
    const counts = new Map()
    for (const p of data?.content ?? []) {
      if (p.category) counts.set(p.category, (counts.get(p.category) || 0) + 1)
    }
    return counts
  }, [data])

  return (
    <MasterDataPage
      title="Categories"
      entity="category"
      storeKey="categories"
      store={categoryStore}
      seed={seed}
      description="Product categories used across your catalog."
      fields={[
        { key: 'name', label: 'Category name', required: true, placeholder: 'e.g. Home & Kitchen' },
        { key: 'description', label: 'Description', placeholder: 'What belongs in this category' },
      ]}
      columns={[
        { key: 'name', header: 'Category', render: (r) => <span className="fw-semibold">{r.name}</span> },
        { key: 'description', header: 'Description', render: (r) => r.description || <span className="text-muted">—</span> },
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

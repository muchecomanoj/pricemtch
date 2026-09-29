import { useQuery } from '@tanstack/react-query'
import { marketplaceService } from '../services/marketplaceService'
import { marketplaceCodes } from '../utils/marketplaces'

// Which marketplaces exist is a backend fact, and it has already changed once:
// Walmart was never live and Web has been removed. Any screen holding its own
// list will offer filters the API rejects with a 400, so every marketplace
// selector reads this instead of a constant.
//
// Cached for ten minutes — the set changes when someone connects a channel in
// Settings, not between page views.
export function useMarketplaceCodes() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['marketplaces'],
    queryFn: marketplaceService.list,
    staleTime: 10 * 60 * 1000,
  })
  return { codes: marketplaceCodes(data), loading: isLoading, error: isError }
}

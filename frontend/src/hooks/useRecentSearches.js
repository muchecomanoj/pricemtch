import { useState } from 'react'

// The last few searches, kept in this browser so a product looked up yesterday
// is one click away instead of retyped. Per user: two people sharing a browser
// should not see each other's lookups.
//
// Only what is needed to run the search again is kept — identifier type,
// value, region, marketplaces and result count. Image searches are not kept:
// the image itself is too large to store and meaningless without it.
const MAX = 8
const storeKey = (userId) => `recentSearches:${userId || 'anon'}`

function read(userId) {
  try {
    const v = JSON.parse(localStorage.getItem(storeKey(userId)) || '[]')
    return Array.isArray(v) ? v.filter((e) => e && typeof e.value === 'string' && e.key) : []
  } catch {
    // A corrupt or unavailable store is not a reason to block searching.
    return []
  }
}

const sameSearch = (a, b) => a.key === b.key
  && a.value.toLowerCase() === b.value.toLowerCase()
  && (a.region || '') === (b.region || '')
  && (a.markets || []).join() === (b.markets || []).join()

export function useRecentSearches(userId) {
  const [recent, setRecent] = useState(() => read(userId))

  const write = (next) => {
    try { localStorage.setItem(storeKey(userId), JSON.stringify(next)) } catch { /* not fatal */ }
    return next
  }

  // Newest first; running the same search again moves it to the top.
  const remember = (form, markets) => {
    const value = (form.value || '').trim()
    if (!value || form.image) return
    const entry = {
      key: form.key, value, region: form.region || '', maxResults: form.maxResults,
      markets: [...(markets || [])].sort(), at: Date.now(),
    }
    setRecent((prev) => write([entry, ...prev.filter((e) => !sameSearch(e, entry))].slice(0, MAX)))
  }

  const forget = (entry) => setRecent((prev) => write(prev.filter((e) => !sameSearch(e, entry))))
  const clear = () => setRecent(write([]))

  return { recent, remember, forget, clear }
}

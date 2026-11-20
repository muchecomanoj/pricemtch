import { useState } from 'react'
import { useAuth } from '../context/AuthContext'

// useState that remembers its value in this browser, per user, so a list opens
// with the filters it was left on. A drop-in for useState:
//
//   const [status, setStatus] = useStoredState('products.status', '')
//
// For standing choices only — dropdown filters and sort order. Typed search
// text and the page number are deliberately not stored: coming back to a
// half-typed query or page 7 of a list that has since changed is confusing.
//
// A stored value of a different type than `initial` (an old format, a hand
// edit) is ignored, and a blocked or full store just means nothing is
// remembered — the page works the same either way.
const key = (userId, name) => `filters:${userId || 'anon'}:${name}`

function read(k, initial) {
  try {
    const raw = localStorage.getItem(k)
    if (raw == null) return initial
    const v = JSON.parse(raw)
    if (typeof v !== typeof initial) return initial
    // Objects merge over the default, so a newly added field gets its default.
    if (initial && typeof initial === 'object' && !Array.isArray(initial)) return { ...initial, ...v }
    return v
  } catch {
    return initial
  }
}

export function useStoredState(name, initial) {
  const { user } = useAuth()
  const k = key(user?.id ?? user?.email, name)
  const [value, setValue] = useState(() => read(k, initial))

  const set = (next) => setValue((prev) => {
    const v = typeof next === 'function' ? next(prev) : next
    try { localStorage.setItem(k, JSON.stringify(v)) } catch { /* not fatal */ }
    return v
  })

  return [value, set]
}

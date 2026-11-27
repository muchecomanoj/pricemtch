import { useCallback, useEffect, useState } from 'react'

// A short pause after a retry that failed again.
//
// `retryable: true` covers two different things: a connector that fell over
// (a retry soon helps) and Amazon refusing our server's address (the next try
// comes from the same address and usually fails the same way). Each retry costs
// ~25s per row, so after one comes back failed the button waits a minute
// instead of inviting ten clicks. There is never an automatic retry.
//
// Kept outside React state, by key, because the component showing the button
// is often unmounted while the retry runs (a spinner takes its place) and
// mounted again with the new answer.
const COOLDOWN_MS = 60_000
const pending = new Set()
const until = new Map()

export function useRetryCooldown(key) {
  const [now, setNow] = useState(() => Date.now())
  const secondsLeft = Math.max(0, Math.ceil(((until.get(key) || 0) - now) / 1000))
  const cooling = secondsLeft > 0

  useEffect(() => {
    if (!cooling) return undefined
    const t = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(t)
  }, [cooling])

  // Call when the user presses retry.
  const markRetried = useCallback(() => { pending.add(key) }, [key])

  // Call when an answer arrives. Starts the pause only if it answers a retry
  // and that retry failed again.
  const settle = useCallback((failed) => {
    if (!pending.delete(key)) return
    if (failed) {
      until.set(key, Date.now() + COOLDOWN_MS)
      setNow(Date.now())
    }
  }, [key])

  return { secondsLeft, markRetried, settle }
}

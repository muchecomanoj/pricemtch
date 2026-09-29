import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { productIntelService } from '../services/productIntelService'
import { useNotification } from '../context/NotificationContext'

// Roughly 15 judgements a minute on the free tier — measured by the backend,
// not guessed. 20 is therefore about 80 seconds, which is why this reports
// progress instead of spinning a button, and why 50 (the cap) is not the
// default: three and a half minutes of a disabled screen is not a UX.
const BATCH = 20
const PER_MINUTE = 15

export const estimateSeconds = (n) => Math.max(5, Math.round((n / PER_MINUTE) * 60))

export function estimateText(n) {
  const s = estimateSeconds(n)
  if (s < 90) return 'about a minute'
  return `about ${Math.round(s / 60)} minutes`
}

// Explicit AI review of one product's candidates.
//
// Deliberately NOT a useQuery: a query would run on mount, on refocus and on
// cache miss, and every one of those spends a metered token budget. This only
// ever fires from a button press.
export function useAiReview(productId, { onDone } = {}) {
  const qc = useQueryClient()
  const { notify } = useNotification()
  const [running, setRunning] = useState(false)
  const [batch, setBatch] = useState(0)

  const run = async (limit = BATCH) => {
    setRunning(true)
    setBatch(limit)
    try {
      const rows = await productIntelService.aiReview(productId, { limit })
      const left = rows.filter((r) => !r.aiVerdict).length
      const judged = rows.length - left
      notify.success(left > 0
        ? `Judged ${judged} of ${rows.length} — ${left} still unjudged.`
        : `All ${rows.length} candidate${rows.length === 1 ? '' : 's'} judged.`)
      // Verdicts arrive attached to the rows, so the tables that render them
      // need refetching rather than the response being used directly.
      qc.invalidateQueries({ queryKey: ['candidates', String(productId)] })
      qc.invalidateQueries({ queryKey: ['matchReview'] })
      onDone?.(rows)
      return rows
    } catch (err) {
      // 409 means no provider is configured. That is a setup state, not a
      // failure of the review — saying "review failed" would send someone
      // looking for a bug that isn't there.
      if (err?.response?.status === 409) {
        notify.info(err.message || 'AI review is not set up yet — no provider is configured.')
      } else {
        notify.error(err?.message || 'Could not run the AI review.')
      }
      return null
    } finally {
      setRunning(false)
      setBatch(0)
    }
  }

  return { run, running, batch, BATCH }
}

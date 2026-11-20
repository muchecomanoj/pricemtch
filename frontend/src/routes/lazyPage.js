import { lazy } from 'react'

// React.lazy, plus recovery from a deploy that happened while the tab was open.
//
// Each page is its own file with a content hash in its name. After a new
// release those old files are gone, so a tab opened before it fails the first
// time it visits a page it hasn't loaded yet ("Failed to fetch dynamically
// imported module"). Reloading fetches the new index.html, which names the new
// files, so that is done once. If the page still fails after the reload, the
// error goes to the ErrorBoundary as usual instead of reloading forever.
const RELOADED = 'chunkReloaded'

const storage = {
  get: () => { try { return sessionStorage.getItem(RELOADED) } catch { return '1' } },
  set: () => { try { sessionStorage.setItem(RELOADED, '1') } catch { /* blocked */ } },
  clear: () => { try { sessionStorage.removeItem(RELOADED) } catch { /* blocked */ } },
}

export function lazyPage(load) {
  return lazy(() => load().then(
    (module) => {
      // Loaded fine, so the next release may reload once again.
      storage.clear()
      return module
    },
    (error) => {
      if (storage.get()) throw error
      storage.set()
      window.location.reload()
      // Never settles: the page is going away, and nothing should render meanwhile.
      return new Promise(() => {})
    },
  ))
}

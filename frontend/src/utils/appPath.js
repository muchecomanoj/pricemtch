// The folder the app is served from — Vite's `base` (vite.config.js), e.g.
// "/pricematch-react". Empty when served from the root.
//
// React Router adds it to every <Link> and navigate() on its own (it is
// passed as the router's basename in App.jsx). Anything that builds an address
// by hand — window.location, a plain <a href>, a URL handed to Stripe or put
// in an email — has to add it itself, through these.
export const APP_BASE = import.meta.env.BASE_URL.replace(/\/+$/, '')

// '/login' → '/pricematch-react/login'
export const appPath = (path) => `${APP_BASE}${path}`

// '/signup' → 'http://host:5174/pricematch-react/signup'
export const appUrl = (path) => `${window.location.origin}${appPath(path)}`

// The in-app route of the current page, base removed: '/pricematch-react/login' → '/login'.
export const currentRoute = () => {
  const p = window.location.pathname
  return APP_BASE && p.startsWith(APP_BASE) ? p.slice(APP_BASE.length) || '/' : p
}

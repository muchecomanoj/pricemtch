# Price Intelligence Platform — Frontend

React 19 + Vite frontend for the AI Product & Competitor Price Intelligence Platform.
Runs fully on **mock JSON** today; swap in the Java Spring Boot backend by changing one config value.

## Requirements
- Node.js **20 or 22 LTS** (see `.nvmrc`)
- npm 10+

## Getting started
```bash
npm install
npm run dev        # http://localhost:5173
```
Login screen accepts any email/password in mock mode (prefilled for convenience).

## Scripts
| Command | Purpose |
|---------|---------|
| `npm run dev` | Start Vite dev server |
| `npm run build` | Production build to `dist/` |
| `npm run preview` | Preview the production build |
| `npm run lint` | Run ESLint |

## Connecting the Java backend
1. Copy `.env.example` → `.env.local`.
2. Set `VITE_USE_MOCK=false` (leave `VITE_API_BASE_URL=/api/v1`).
3. Ensure backend routes match `src/services/apiEndpoints.js`.

No component code changes are needed — every service already calls the real API when `USE_MOCK` is false.

### Choosing a backend
Requests always go to the relative path `/api/v1`; the Vite dev proxy forwards
them, which keeps everything same-origin so **no CORS config is required**.
Which backend it forwards to is picked by mode:

| Command | Mode file | Backend |
|---------|-----------|---------|
| `npm run dev` (default) | `.env.shared` | `http://192.168.10.253:8080` (team server) |
| `npm run dev:local` | `.env.development` | `http://localhost:8080` (your machine) |

`dev:local` only works if you are running the Spring Boot backend yourself —
otherwise every request fails with a connection error.

Same split for builds: `npm run build` / `npm run build:local`.

Vite reads env files once at boot, so **switching modes means restarting the dev
server**. To override both on your machine only, set `VITE_API_PROXY_TARGET` in
`.env.local`.

## Architecture
```
src/
  services/     Axios instance (api.js), endpoint map (apiEndpoints.js), per-domain services
  mock/         Dummy JSON + mock helpers (latency, pagination)
  context/      Auth, Theme, Notification, Dashboard providers
  routes/       AppRoutes (lazy) + ProtectedRoute (auth + role guard)
  layouts/      MainLayout (sidebar + navbar + footer)
  components/   common, layout, tables, cards, charts, forms (reusable)
  pages/        16 feature pages + error pages
  utils/        formatters, markdown
  constants/    roles, navigation, enums
  styles/       SCSS (imports Bootstrap 5, dark mode via data-bs-theme)
```

### Key seam: mock → real API
Each service follows this pattern:
```js
export const productService = {
  async list(params) {
    if (USE_MOCK) return (await mockResponse(paginate(store, params))).data
    return (await api.get(ENDPOINTS.products.list, { params })).data
  },
}
```

## Roles
`ADMIN`, `MANAGER`, `ANALYST`, `READ_ONLY` — the sidebar and routes filter by role
(`constants/navigation.js`, `ProtectedRoute`).

## Tech
React 19 · Vite 6 · React Router 7 · React Query · Bootstrap 5 · SCSS · Axios ·
React Hook Form · Chart.js · React Icons · React Toastify · SweetAlert2 · Context API

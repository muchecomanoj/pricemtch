import api from './api'
import { ENDPOINTS } from './apiEndpoints'
import { mockResponse } from '../mock/mockHelper'
import { ASSIGNABLE_ROLES, ROLE_LABELS } from '../constants'

// ---------------------------------------------------------------------------
// Roles master data.
//   • list()  -> LIVE today (GET /api/v1/roles)
//   • create/update/remove -> the backend does NOT expose these yet, so they
//     run against an in-memory store seeded from the live list. The real API
//     calls are written and gated by ROLES_WRITE_LIVE — flip it to true (or
//     just remove the flag) the day POST/PUT/DELETE /roles ship. No component
//     changes required.
// ---------------------------------------------------------------------------
const ROLES_WRITE_LIVE = false

// In-memory working copy so add/edit/delete are reflected immediately in the UI.
let store = null

async function seed() {
  if (store) return store
  // Access types are a fixed backend enum now (the /roles endpoint was removed),
  // so the catalog is derived from the assignable list.
  store = ASSIGNABLE_ROLES.map((name) => ({ name, label: ROLE_LABELS[name] || name, description: '' }))
  return store
}

export const roleService = {
  async list() {
    await seed()
    return (await mockResponse(store, 200)).data
  },

  async create(role) {
    if (ROLES_WRITE_LIVE) {
      const { data } = await api.post(ENDPOINTS.roles.create, role)
      store = null // force reseed
      return data
    }
    await seed()
    if (store.some((r) => r.name === role.name)) throw new Error(`Role "${role.name}" already exists`)
    store = [...store, { ...role }]
    return (await mockResponse(role)).data
  },

  async update(name, patch) {
    if (ROLES_WRITE_LIVE) {
      const { data } = await api.put(ENDPOINTS.roles.byName(name), patch)
      store = null
      return data
    }
    await seed()
    store = store.map((r) => (r.name === name ? { ...r, ...patch } : r))
    return (await mockResponse(store.find((r) => r.name === name))).data
  },

  async remove(name) {
    if (ROLES_WRITE_LIVE) {
      const { data } = await api.delete(ENDPOINTS.roles.byName(name))
      store = null
      return data
    }
    await seed()
    store = store.filter((r) => r.name !== name)
    return (await mockResponse({ ok: true })).data
  },
}

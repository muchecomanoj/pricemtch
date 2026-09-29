// User administration. Everything else that once lived here is now live in its
// own service — see the note at the bottom of the file.
import api, { USE_MOCK } from './api'
import { ENDPOINTS } from './apiEndpoints'
import { mockResponse } from '../mock/mockHelper'
import { users } from '../mock/data'
import { mapUser, mapPage } from './mappers'

// ── LIVE ────────────────────────────────────────────────────────
export const userService = {
  async list() {
    if (USE_MOCK) return (await mockResponse(users)).data
    const { data } = await api.get(ENDPOINTS.users.list, { params: { page: 0, size: 100 } })
    return mapPage(data, mapUser).content
  },
  async save(payload) {
    if (USE_MOCK) return (await mockResponse(payload)).data

    // Split the single "name" field the form uses into first/last for the backend.
    const [firstName, ...rest] = (payload.name || '').trim().split(' ')
    const lastName = rest.join(' ')
    const accessType = payload.accessType || payload.role

    // CREATE — CreateUserRequest: firstName, lastName, email, phone, password, accessType.
    if (!payload.id) {
      const { data } = await api.post(ENDPOINTS.users.list, {
        firstName: firstName || payload.email,
        lastName,
        email: payload.email,
        phone: payload.phone || null,
        password: payload.password,
        accessType,
      })
      return mapUser(data)
    }

    // UPDATE — UpdateUserRequest: firstName, lastName, phone, accessType, profileImage.
    const { data } = await api.put(ENDPOINTS.users.byId(payload.id), {
      firstName: firstName || payload.email,
      lastName,
      phone: payload.phone || null,
      accessType,
      profileImage: payload.profileImage || null,
    })

    // Status changes still use the dedicated activate/deactivate endpoints.
    if (payload.__originalActive != null && payload.active !== payload.__originalActive) {
      await api.patch(payload.active ? ENDPOINTS.users.activate(payload.id) : ENDPOINTS.users.deactivate(payload.id))
    }
    return mapUser(data)
  },
  async remove(id) {
    if (USE_MOCK) return (await mockResponse({ ok: true })).data
    return (await api.delete(ENDPOINTS.users.byId(id))).data
  },

  // NOTE: a user editing THEMSELVES goes through profileService (GET/PUT
  // /profile), which cannot change accessType. save() above is for admins
  // managing other people and does accept a role.

  // PATCH /users/{id}/activate | /deactivate — dedicated status endpoints.
  async setActive(id, active) {
    if (USE_MOCK) return (await mockResponse({ id, active })).data
    const url = active ? ENDPOINTS.users.activate(id) : ENDPOINTS.users.deactivate(id)
    const { data } = await api.patch(url)
    return mapUser(data)
  },
  // POST /users/{id}/reset-password — admin triggers a password reset.
  async resetPassword(id) {
    if (USE_MOCK) return (await mockResponse({ ok: true })).data
    return (await api.post(ENDPOINTS.users.resetPassword(id))).data
  },
}

// ── All former mock services here are now LIVE elsewhere ────────
//   competitorService → competitorListingService  (GET /competitor-listings)
//   matchService      → competitorListingService  (GET /match-review)
//   priceService      → productIntelService       (GET /products/{id}/market-prices)
//   costService       → costModelService          (GET|PUT /cost-model)
//   profitService     → productIntelService       (GET /products/{id}/profitability)
// Nothing mock-only remains in this file.

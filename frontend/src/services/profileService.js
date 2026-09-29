import api, { USE_MOCK } from './api'
import { ENDPOINTS } from './apiEndpoints'
import { mockResponse } from '../mock/mockHelper'
import { mockUser } from '../mock/data'
import { mapUser } from './mappers'

// The signed-in user's own profile.
//
// Deliberately NOT /users/{id}: that accepts accessType, so a self-service form
// posting to it could let someone promote their own role. /profile exposes only
// firstName, lastName, phone and profileImage.

// Mirrors the backend's own constraint so a bad value is caught before the 400.
export const PHONE_PATTERN = /^$|^[+]?[0-9\-\s()]{7,20}$/

export const profileService = {
  async get() {
    if (USE_MOCK) return (await mockResponse(mockUser)).data
    return mapUser((await api.get(ENDPOINTS.profile)).data)
  },

  // Partial update — only the keys that actually changed are sent, and `email`
  // is never among them.
  async update(values, original = {}) {
    const body = {}
    for (const key of ['firstName', 'lastName', 'phone', 'profileImage']) {
      const next = values[key] ?? ''
      const prev = original[key] ?? ''
      if (next !== prev) body[key] = next === '' ? null : next
    }
    if (USE_MOCK) return { ...original, ...body }
    return mapUser((await api.put(ENDPOINTS.profile, body)).data)
  },
}

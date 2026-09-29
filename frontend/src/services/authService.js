import api, { USE_MOCK } from './api'
import { ENDPOINTS } from './apiEndpoints'
import { mockResponse } from '../mock/mockHelper'
import { mockUser, mockToken } from '../mock/data'
import { mapAuth, mapUser } from './mappers'

export const authService = {
  async login({ email, password }) {
    if (USE_MOCK) {
      if (!email || !password) throw new Error('Email and password required')
      return { token: mockToken, user: mockUser }
    }
    const { data } = await api.post(ENDPOINTS.auth.login, { email, password })
    // AuthResponse signals a 2FA challenge instead of returning tokens.
    if (data?.twoFactorRequired) {
      return { twoFactorRequired: true, email, challengeToken: data.challengeToken }
    }
    return mapAuth(data) // { token, refreshToken, user }
  },

  async me() {
    if (USE_MOCK) return (await mockResponse(mockUser)).data
    const { data } = await api.get(ENDPOINTS.auth.me)
    return mapUser(data)
  },

  async logout() {
    if (USE_MOCK) return { ok: true }
    // Backend expects the refresh token to revoke the session.
    const refreshToken = localStorage.getItem('refreshToken')
    const { data } = await api.post(ENDPOINTS.auth.logout, { refreshToken })
    return data
  },

  // POST /auth/change-password — { currentPassword, newPassword }
  async changePassword({ currentPassword, newPassword }) {
    if (USE_MOCK) return (await mockResponse({ ok: true })).data
    const { data } = await api.post(ENDPOINTS.auth.changePassword, { currentPassword, newPassword })
    return data
  },

  // ── Password reset (no auth) ────────────────────────────────────
  // Three steps against the live contract; every call carries the email, and
  // steps 2-3 also carry the 6-digit code from the reset email.

  // Step 1 — email a 6-digit reset code to the registered address.
  async forgotPassword(email) {
    if (USE_MOCK) return (await mockResponse({ ok: true })).data
    const { data } = await api.post(ENDPOINTS.auth.forgotPassword, { email })
    return data
  },

  // Step 2 — verify the code before letting the user pick a new password.
  async verifyResetCode(email, code) {
    if (USE_MOCK) {
      if (code !== '123456') throw new Error('Invalid code. Use 123456 in demo mode.')
      return (await mockResponse({ ok: true })).data
    }
    const { data } = await api.post(ENDPOINTS.auth.verifyResetCode, { email, code })
    return data
  },

  // Step 3 — set the new password using the verified code.
  async resetPassword(email, code, newPassword) {
    if (USE_MOCK) return (await mockResponse({ ok: true })).data
    const { data } = await api.post(ENDPOINTS.auth.resetPassword, { email, code, newPassword })
    return data
  },
}

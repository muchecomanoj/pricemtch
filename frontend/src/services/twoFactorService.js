import api from './api'
import { ENDPOINTS } from './apiEndpoints'
import { mapAuth } from './mappers'

// ---------------------------------------------------------------------------
// Two-factor authentication — LIVE against the backend contract:
//   GET  /auth/2fa/status   -> TwoFactorStatusResponse { enabled }
//   POST /auth/2fa/setup    -> TwoFactorSetupResponse  { secret, otpauthUrl }
//   POST /auth/2fa/enable   { code }            -> TwoFactorEnableResponse { enabled, backupCodes[] }
//   POST /auth/2fa/disable  { code }            -> TwoFactorStatusResponse { enabled }
//   POST /auth/2fa/verify   { code, challengeToken } -> AuthResponse (full session)
//   POST /auth/2fa/resend   { challengeToken }
// ---------------------------------------------------------------------------
const E = ENDPOINTS.auth.twoFactor

export const twoFactorService = {
  // Is 2FA enabled for the signed-in user?
  async status() {
    const { data } = await api.get(E.status)
    return data // { enabled }
  },

  // Begin enrollment — returns the secret + otpauth URL to render as a QR code.
  async setup() {
    const { data } = await api.post(E.setup)
    return data // { secret, otpauthUrl }
  },

  // Confirm enrollment with a code from the authenticator app.
  async enable(code) {
    const { data } = await api.post(E.enable, { code })
    return data // { enabled, backupCodes[] }
  },

  // Turn 2FA off (requires a valid code).
  async disable(code) {
    const { data } = await api.post(E.disable, { code })
    return data // { enabled }
  },

  // Verify the OTP during login -> returns the real session.
  async verify({ code, challengeToken }) {
    const { data } = await api.post(E.verify, { code, challengeToken })
    return mapAuth(data) // { token, refreshToken, user }
  },

  // Re-send / re-generate the OTP for an in-flight login challenge.
  async resend(challengeToken) {
    const { data } = await api.post(E.resend, { challengeToken })
    return data
  },
}

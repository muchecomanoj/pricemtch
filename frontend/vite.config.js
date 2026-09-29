import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// Dev proxy forwards /api to the Java Spring Boot backend, which keeps requests
// same-origin so the backend needs no CORS config.
//
// The target is env-driven — set VITE_API_PROXY_TARGET in .env.local to point at
// a different backend without editing this file:
//   http://localhost:8080        (local backend — the default)
//   http://192.168.10.253:8080   (shared dev server)
// Changing it requires restarting the dev server; Vite reads this once at boot.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiTarget = env.VITE_API_PROXY_TARGET || 'http://localhost:8080'

  return {
    plugins: [react()],
    css: {
      preprocessorOptions: {
        scss: {
          // Bootstrap 5.3 still uses legacy @import internally (fixed in Bootstrap 6).
          // Silence those third-party deprecation warnings so our build stays clean.
          quietDeps: true,
          silenceDeprecations: ['import', 'global-builtin', 'color-functions', 'if-function'],
        },
      },
    },
    server: {
      // host:true makes the dev server listen on ALL network interfaces, not just
      // localhost — so other machines on the LAN can reach it via your IP
      // (e.g. http://192.168.10.42:5174). Needed because activation-email links
      // opened on a different computer can't use "localhost".
      host: true,
      // Pin the port. The backend builds activation-email links with this exact
      // host+port, so the dev server MUST stay here. strictPort makes Vite FAIL
      // LOUDLY if the port is taken instead of silently drifting to 5175 (which
      // orphaned the emailed links).
      port: 5174,
      strictPort: true,
      proxy: {
        // Forward /api/* to the Spring Boot backend (avoids browser CORS in dev).
        '/api': {
          target: apiTarget,
          changeOrigin: true,
        },
      },
    },
  }
})

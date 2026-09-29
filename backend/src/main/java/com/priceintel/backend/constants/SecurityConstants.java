package com.priceintel.backend.constants;

/**
 * Constants used across the security layer. Centralising these avoids
 * "magic strings" scattered through the code (SOLID / single source of truth).
 */
public final class SecurityConstants {

    private SecurityConstants() {
        // utility class - no instances
    }

    public static final String TOKEN_PREFIX = "Bearer ";
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String ROLE_PREFIX = "ROLE_";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_TOKEN_TYPE = "tokenType";

    /**
     * Public endpoints that never require authentication. Only the auth routes
     * that a logged-out user must reach are listed here — /me and
     * /change-password are deliberately NOT public and require a valid token.
     */
    public static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/verify-reset-code",
            "/api/v1/auth/reset-password",
            // 2FA login steps: the caller holds only a challenge token, not a session.
            "/api/v1/auth/2fa/verify",
            "/api/v1/auth/2fa/resend",
            "/api/public/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/swagger-resources/**",
            "/webjars/**",
            "/actuator/health"
    };
}

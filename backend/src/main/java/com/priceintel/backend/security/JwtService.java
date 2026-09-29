package com.priceintel.backend.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.priceintel.backend.constants.SecurityConstants;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates and validates JWTs. Access tokens carry tenant/access-type claims so
 * the token is self-describing (enforcement still re-checks the database).
 * "Remember me" extends the refresh token lifetime.
 */
@Slf4j
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;
    private final long rememberMeRefreshExpirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiration-ms}") long accessTokenExpirationMs,
            @Value("${app.jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs,
            @Value("${app.jwt.remember-me-expiration-ms:2592000000}") long rememberMeRefreshExpirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
        this.rememberMeRefreshExpirationMs = rememberMeRefreshExpirationMs;
    }

    public String generateAccessToken(CustomUserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("superAdmin", userDetails.isSuperAdmin());
        claims.put("tenantId", userDetails.getTenantId());
        claims.put("userId", userDetails.getUserId());
        String role = userDetails.isSuperAdmin() ? "SUPER_ADMIN"
                : (userDetails.getUser().getAccessType() != null
                        ? userDetails.getUser().getAccessType().name() : null);
        claims.put("role", role);
        if (userDetails.getUser().getAccessType() != null) {
            claims.put("accessType", userDetails.getUser().getAccessType().name());
        }
        return buildToken(userDetails.getUsername(), claims, accessTokenExpirationMs, "ACCESS");
    }

    public String generateRefreshToken(CustomUserDetails userDetails, boolean rememberMe) {
        long expiry = rememberMe ? rememberMeRefreshExpirationMs : refreshTokenExpirationMs;
        return buildToken(userDetails.getUsername(), Map.of(), expiry, "REFRESH");
    }

    /**
     * A refresh token that lasts exactly {@code ttlMs}.
     *
     * <p>Used when a session is continued rather than started: the replacement
     * token ends when the session was always going to end, so refreshing keeps
     * someone signed in but never extends the session past its limit.</p>
     */
    public String generateRefreshToken(CustomUserDetails userDetails, long ttlMs) {
        return buildToken(userDetails.getUsername(), Map.of(), ttlMs, "REFRESH");
    }

    /** Token type for the short-lived 2FA challenge issued between password and OTP. */
    public static final String TOKEN_TYPE_2FA_CHALLENGE = "2FA_CHALLENGE";

    /** Five minutes to enter the OTP. */
    private static final long CHALLENGE_EXPIRATION_MS = 5 * 60 * 1000L;

    /**
     * Issues the short-lived token that "holds" the session after a correct
     * password while the user completes two-factor verification. It grants no
     * access on its own.
     */
    public String generateTwoFactorChallengeToken(String email, boolean rememberMe) {
        return buildToken(email, Map.of("rememberMe", rememberMe),
                CHALLENGE_EXPIRATION_MS, TOKEN_TYPE_2FA_CHALLENGE);
    }

    public String extractTokenType(String token) {
        return extractClaim(token, c -> c.get(SecurityConstants.CLAIM_TOKEN_TYPE, String.class));
    }

    public boolean extractRememberMe(String token) {
        Boolean value = extractClaim(token, c -> c.get("rememberMe", Boolean.class));
        return Boolean.TRUE.equals(value);
    }

    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationMs / 1000;
    }

    public long getRefreshTokenExpirationMs(boolean rememberMe) {
        return rememberMe ? rememberMeRefreshExpirationMs : refreshTokenExpirationMs;
    }

    private String buildToken(String subject, Map<String, Object> claims, long expirationMs, String tokenType) {
        Date now = new Date();
        return Jwts.builder()
                .id(java.util.UUID.randomUUID().toString())
                .subject(subject)
                .claims(claims)
                .claim(SecurityConstants.CLAIM_TOKEN_TYPE, tokenType)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(extractAllClaims(token));
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    }

    public boolean isTokenValid(String token, String username) {
        try {
            return extractUsername(token).equals(username) && !isTokenExpired(token);
        } catch (Exception e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }
}

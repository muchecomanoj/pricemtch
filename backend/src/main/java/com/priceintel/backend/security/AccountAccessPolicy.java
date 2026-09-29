package com.priceintel.backend.security;

import java.util.List;

import org.springframework.util.AntPathMatcher;

import com.priceintel.backend.constants.AccountAccess;

/**
 * Which requests each level of access may make.
 *
 * <p>READ_ONLY is "read, and pay": every GET that reads stored data, plus the
 * few writes a customer needs to get back in — signing in and out, their own
 * profile, company billing details, and a paid renewal or upgrade. Everything else that
 * writes is refused, and so are the GETs that are not reads at all but live
 * marketplace calls, because those spend quota the customer is no longer
 * paying for.</p>
 *
 * <p>Downgrade is deliberately not on the list. A scheduled downgrade is
 * applied with a fresh billing period and no payment, so an expired customer
 * who could schedule one would reactivate themselves for free.</p>
 */
public final class AccountAccessPolicy {

    private AccountAccessPolicy() {
    }

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    /** Allowed with any method while expired. */
    static final List<String> READ_ONLY_ANY_METHOD = List.of(
            "/api/v1/auth/**",
            "/api/v1/profile",
            "/api/v1/profile/**",
            "/api/v1/client/company",
            "/api/v1/client/subscription/upgrade",
            // Paying for another period of the same plan — the usual way back in.
            "/api/v1/client/subscription/renew",
            "/api/v1/notifications/**",
            // Reports are built from stored data — the customer's own history.
            "/api/v1/reports/**");

    /**
     * GETs refused while expired: they fetch live from Amazon, eBay or Keepa
     * rather than reading what is stored.
     */
    static final List<String> READ_ONLY_LIVE_GETS = List.of(
            "/api/v1/marketplaces/*/listings/**",
            "/api/v1/marketplaces/amazon/**",
            "/api/v1/marketplaces/ebay/**");

    /** All a blocked account may do: find out why, and sign out. */
    static final List<String> BLOCKED_ALLOWED = List.of(
            "/api/v1/auth/me",
            "/api/v1/auth/logout",
            "/api/v1/auth/refresh");

    public static boolean allows(AccountAccess access, String method, String path) {
        if (access == null || access == AccountAccess.FULL) {
            return true;
        }
        if (path == null || !path.startsWith("/api/v1/")) {
            // Public endpoints, docs and static files are not tenant work.
            return true;
        }
        if (access == AccountAccess.BLOCKED) {
            return matchesAny(BLOCKED_ALLOWED, path);
        }
        if (matchesAny(READ_ONLY_ANY_METHOD, path)) {
            return true;
        }
        boolean read = "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method);
        return read && !matchesAny(READ_ONLY_LIVE_GETS, path);
    }

    private static boolean matchesAny(List<String> patterns, String path) {
        for (String p : patterns) {
            if (MATCHER.match(p, path)) {
                return true;
            }
        }
        return false;
    }
}

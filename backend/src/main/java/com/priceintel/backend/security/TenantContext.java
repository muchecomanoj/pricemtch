package com.priceintel.backend.security;

/**
 * Holds the current request's tenant scope in a thread-local, so services can
 * enforce tenant isolation without threading the tenant id through every call.
 * Set by {@link JwtAuthenticationFilter} after authentication and cleared at the
 * end of the request.
 */
public final class TenantContext {

    private TenantContext() {
    }

    private record Scope(Long tenantId, boolean superAdmin) {
    }

    private static final ThreadLocal<Scope> CONTEXT = new ThreadLocal<>();

    public static void set(Long tenantId, boolean superAdmin) {
        CONTEXT.set(new Scope(tenantId, superAdmin));
    }

    /** Current tenant id, or null for the SUPER_ADMIN / unauthenticated. */
    public static Long getTenantId() {
        Scope s = CONTEXT.get();
        return s == null ? null : s.tenantId();
    }

    public static boolean isSuperAdmin() {
        Scope s = CONTEXT.get();
        return s != null && s.superAdmin();
    }

    /**
     * The tenant every query in this request must be restricted to, or
     * {@code null} for the platform owner, who legitimately sees all tenants.
     *
     * <p>Use this rather than {@link #getTenantId()} when filtering a query:
     * getTenantId alone returns null for the super admin <i>and</i> for an
     * unauthenticated caller, so treating its null as "no filter" would open
     * the data up. This makes the super-admin case the explicit one.</p>
     */
    public static Long scopeOrAllForSuperAdmin() {
        return isSuperAdmin() ? null : getTenantId();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}

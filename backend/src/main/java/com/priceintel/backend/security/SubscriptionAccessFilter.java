package com.priceintel.backend.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceintel.backend.constants.AccountAccess;
import com.priceintel.backend.exception.AccountAccessException;
import com.priceintel.backend.service.impl.SubscriptionAccessService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Checks the company's subscription on every authenticated request.
 *
 * <p>Before this, the subscription was checked only at login. A company that
 * was suspended — or whose plan ran out — kept working for as long as its
 * session lasted, and refreshing the session never looked again.</p>
 *
 * <p>Runs after the JWT filter, so it sees who is calling. The platform owner
 * has no tenant and is never restricted. Deliberately not a Spring bean: a
 * bean filter is also registered on the plain servlet chain, where it would
 * run before authentication and see nobody.</p>
 */
@Slf4j
public class SubscriptionAccessFilter extends OncePerRequestFilter {

    private final SubscriptionAccessService accessService;
    private final ObjectMapper objectMapper;

    public SubscriptionAccessFilter(SubscriptionAccessService accessService, ObjectMapper objectMapper) {
        this.accessService = accessService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails user)
                || user.isSuperAdmin() || user.getTenantId() == null) {
            chain.doFilter(request, response);
            return;
        }

        AccountAccess access = accessService.accessForTenant(user.getTenantId());
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (AccountAccessPolicy.allows(access, request.getMethod(), path)) {
            chain.doFilter(request, response);
            return;
        }

        String code = access == AccountAccess.BLOCKED
                ? AccountAccessException.ACCOUNT_BLOCKED : AccountAccessException.SUBSCRIPTION_EXPIRED;
        String message = access == AccountAccess.BLOCKED
                ? "Your organization cannot be accessed right now. Contact the platform administrator."
                : "Your plan has expired. Renew to use this feature — your data is still available to view.";
        log.info("Refused {} {} for tenant {} ({})", request.getMethod(), path, user.getTenantId(), code);

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                AccountAccessException.body(code, access, message));
    }
}

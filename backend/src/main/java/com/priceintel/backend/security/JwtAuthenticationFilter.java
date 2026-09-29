package com.priceintel.backend.security;

import java.io.IOException;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.priceintel.backend.constants.SecurityConstants;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Authenticates the request from a Bearer JWT and establishes the tenant scope
 * ({@link TenantContext}) for the duration of the request.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader(SecurityConstants.HEADER_AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith(SecurityConstants.TOKEN_PREFIX)) {
            final String token = authHeader.substring(SecurityConstants.TOKEN_PREFIX.length());
            try {
                final String username = jwtService.extractUsername(token);
                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    CustomUserDetails userDetails =
                            (CustomUserDetails) userDetailsService.loadUserByUsername(username);
                    if (jwtService.isTokenValid(token, userDetails.getUsername()) && userDetails.isEnabled()) {
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails, null, userDetails.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        // Establish tenant scope from the authenticated user (DB is source of truth).
                        TenantContext.set(userDetails.getTenantId(), userDetails.isSuperAdmin());
                    }
                }
            } catch (Exception e) {
                log.debug("Could not authenticate request: {}", e.getMessage());
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}

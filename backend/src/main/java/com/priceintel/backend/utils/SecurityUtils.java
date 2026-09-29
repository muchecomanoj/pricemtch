package com.priceintel.backend.utils;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.priceintel.backend.security.CustomUserDetails;

/**
 * Small helper to read the currently authenticated user from the security
 * context. Keeps that logic in one place.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<String> getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails userDetails) {
            return Optional.of(userDetails.getUsername());
        }
        if (principal instanceof String s && !"anonymousUser".equals(s)) {
            return Optional.of(s);
        }
        return Optional.empty();
    }
}

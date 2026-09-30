package com.priceintel.backend.utils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The caller's IP address, as well as it can be known.
 *
 * <p>Used only to group submissions for throttling, never to authorise
 * anything. Behind Nginx every request arrives from the proxy, so
 * {@code X-Forwarded-For} is read first — but that header is set by the client
 * when there is no proxy in front, so a caller can choose what it says. That is
 * tolerable for rate limiting a contact form and would not be for anything
 * else.</p>
 */
public final class CallerAddress {

    private CallerAddress() { }

    public static String of(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // "client, proxy1, proxy2" — the first entry is the original caller.
            int comma = forwarded.indexOf(',');
            String first = comma > 0 ? forwarded.substring(0, comma) : forwarded;
            return first.trim();
        }
        return request.getRemoteAddr();
    }
}

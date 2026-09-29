package com.priceintel.backend.exception;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import com.priceintel.backend.constants.AccountAccess;

import lombok.Getter;

/**
 * A request refused because of the company's subscription, not the user's role.
 *
 * <p>Carries a {@code code} the screen can branch on without reading the
 * message: {@code SUBSCRIPTION_EXPIRED} means "show the renew prompt",
 * {@code ACCOUNT_BLOCKED} means "sign out and explain". Spring's own
 * AccessDeniedException could not do this — the global handler replaces its
 * message with a generic one, which is why a suspended company used to be told
 * only "You do not have permission to access this resource".</p>
 */
@Getter
public class AccountAccessException extends RuntimeException {

    public static final String SUBSCRIPTION_EXPIRED = "SUBSCRIPTION_EXPIRED";
    public static final String ACCOUNT_BLOCKED = "ACCOUNT_BLOCKED";

    private final String code;
    private final AccountAccess access;

    public AccountAccessException(String code, AccountAccess access, String message) {
        super(message);
        this.code = code;
        this.access = access;
    }

    public static AccountAccessException blocked(String message) {
        return new AccountAccessException(ACCOUNT_BLOCKED, AccountAccess.BLOCKED, message);
    }

    public static AccountAccessException expired(String message) {
        return new AccountAccessException(SUBSCRIPTION_EXPIRED, AccountAccess.READ_ONLY, message);
    }

    /**
     * The response body — the usual envelope plus {@code code} and
     * {@code access}. Built here so the request filter, which answers before any
     * controller exists, and the exception handler send exactly the same shape.
     */
    public static Map<String, Object> body(String code, AccountAccess access, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        body.put("code", code);
        body.put("access", access == null ? null : access.name());
        body.put("data", null);
        body.put("errors", null);
        body.put("timestamp", LocalDateTime.now().toString());
        return body;
    }

    public Map<String, Object> body() {
        return body(code, access, getMessage());
    }
}

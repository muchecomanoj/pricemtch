package com.priceintel.backend.exception;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.priceintel.backend.dto.response.ApiResponse;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Central place that turns exceptions into clean, consistent HTTP responses.
 * Every handler returns the same {@link ApiResponse} envelope.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Bean Validation failures on @Valid request bodies -> 400 with field errors. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        log.warn("Validation failed: {}", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Validation failed", fieldErrors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("Constraint violation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Validation failed", ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Object>> handleDuplicate(DuplicateResourceException ex) {
        log.warn("Duplicate resource: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadRequest(BadRequestException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * A path or query value that could not be converted to its parameter type.
     *
     * <p>Spring reports these as a type mismatch, which reaches the user as "An
     * unexpected error occurred" — accurate for the framework, useless for
     * whoever typed the value. Where the converter raised its own explanation
     * (naming the valid marketplaces, say), that message is unwrapped and used;
     * otherwise the parameter is at least named.</p>
     */
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleTypeMismatch(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
        Throwable cause = ex.getCause();
        while (cause != null && !(cause instanceof BadRequestException)) {
            cause = cause.getCause();
        }
        String message = cause instanceof BadRequestException bad
                ? bad.getMessage()
                : "'" + ex.getValue() + "' is not a valid value for " + ex.getName() + ".";
        log.warn("Bad request parameter: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(message));
    }

    @ExceptionHandler(TokenRefreshException.class)
    public ResponseEntity<ApiResponse<Object>> handleTokenRefresh(TokenRefreshException ex) {
        log.warn("Token refresh failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MarketplaceApiException.class)
    public ResponseEntity<ApiResponse<Object>> handleMarketplaceApi(MarketplaceApiException ex) {
        log.warn("Marketplace API error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(AiException.class)
    public ResponseEntity<ApiResponse<Object>> handleAi(AiException ex) {
        log.warn("AI provider error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Bad credentials");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid email or password"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuthentication(AuthenticationException ex) {
        log.warn("Authentication error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Authentication failed"));
    }

    /** Refused because of the company's subscription — the message and code are meant for the user. */
    @ExceptionHandler(AccountAccessException.class)
    public ResponseEntity<Map<String, Object>> handleAccountAccess(AccountAccessException ex) {
        log.info("Account access refused ({}): {}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.body());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("You do not have permission to access this resource"));
    }

    /** Anything not handled above -> 500. We never leak stack traces to clients. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred"));
    }
}

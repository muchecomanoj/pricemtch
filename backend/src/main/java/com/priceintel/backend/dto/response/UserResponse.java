package com.priceintel.backend.dto.response;

import java.time.LocalDateTime;

import com.priceintel.backend.constants.AccessType;
import com.priceintel.backend.constants.UserStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Safe view of a user (no password). Includes tenant scope and access type.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private Long tenantId;
    private boolean superAdmin;

    /**
     * Single, unambiguous role for the frontend to switch UI on:
     * "SUPER_ADMIN" for the platform owner, otherwise the tenant access type
     * (ADMIN, MANAGER, ANALYST, FINANCE, VIEWER).
     */
    private String role;

    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private AccessType accessType;
    private String profileImage;
    private UserStatus status;
    private LocalDateTime lastLogin;
    private LocalDateTime createdAt;

    /**
     * The company's standing — sent with login and /auth/me only, so the screen
     * can show an expiry banner. Absent in user lists, and for the platform owner.
     */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private AccountStatusResponse account;
}

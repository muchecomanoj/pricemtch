package com.priceintel.backend.service;

import com.priceintel.backend.constants.AccessType;
import com.priceintel.backend.constants.UserStatus;
import com.priceintel.backend.dto.request.CreateUserRequest;
import com.priceintel.backend.dto.request.ResetUserPasswordRequest;
import com.priceintel.backend.dto.request.UpdateUserRequest;
import com.priceintel.backend.dto.response.PagedResponse;
import com.priceintel.backend.dto.response.UserResponse;

/**
 * Tenant-scoped user management, performed by a Client (tenant ADMIN). Every
 * operation is confined to the caller's tenant.
 */
public interface UserService {

    UserResponse createUser(CreateUserRequest request);

    UserResponse getUser(Long id);

    PagedResponse<UserResponse> listUsers(String keyword, AccessType accessType, UserStatus status,
                                          int page, int size, String sortBy, String direction);

    UserResponse updateUser(Long id, UpdateUserRequest request);

    /** Current user's own profile (Settings → Profile). */
    UserResponse getProfile(Long userId);

    /** Self-service update of the current user's profile. */
    UserResponse updateProfile(Long userId, com.priceintel.backend.dto.request.UpdateProfileRequest request);

    void softDeleteUser(Long id);

    UserResponse activateUser(Long id);

    UserResponse deactivateUser(Long id);

    void resetPassword(Long id, ResetUserPasswordRequest request);
}

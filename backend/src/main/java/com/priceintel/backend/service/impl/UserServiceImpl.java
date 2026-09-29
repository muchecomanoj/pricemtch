package com.priceintel.backend.service.impl;

import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.constants.AccessType;
import com.priceintel.backend.constants.UserStatus;
import com.priceintel.backend.dto.request.CreateUserRequest;
import com.priceintel.backend.dto.request.ResetUserPasswordRequest;
import com.priceintel.backend.dto.request.UpdateUserRequest;
import com.priceintel.backend.dto.response.PagedResponse;
import com.priceintel.backend.dto.response.UserResponse;
import com.priceintel.backend.entity.Tenant;
import com.priceintel.backend.entity.User;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.exception.DuplicateResourceException;
import com.priceintel.backend.exception.ResourceNotFoundException;
import com.priceintel.backend.mapper.UserMapper;
import com.priceintel.backend.repository.RefreshTokenRepository;
import com.priceintel.backend.repository.TenantRepository;
import com.priceintel.backend.repository.UserRepository;
import com.priceintel.backend.repository.UserSpecifications;
import com.priceintel.backend.security.TenantContext;
import com.priceintel.backend.service.UserService;
import com.priceintel.backend.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "firstName", "lastName", "email", "accessType", "status", "createdAt", "lastLogin");

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        Long tenantId = requireTenant();
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email is already registered: " + request.getEmail());
        }
        // Enforce the tenant's max-users limit.
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        long active = userRepository.countByTenantIdAndStatusNot(tenantId, UserStatus.DELETED);
        if (active >= tenant.getMaxUsers()) {
            throw new BadRequestException("User limit reached for your plan (" + tenant.getMaxUsers() + ")");
        }

        User user = User.builder()
                .tenantId(tenantId)
                .superAdmin(false)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .password(passwordEncoder.encode(request.getPassword()))
                .accessType(request.getAccessType())
                .status(UserStatus.ACTIVE)
                .build();
        user = userRepository.save(user);
        log.info("Tenant {} created user {}", tenantId, user.getEmail());
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUser(Long id) {
        return userMapper.toResponse(findInTenant(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> listUsers(String keyword, AccessType accessType, UserStatus status,
                                                 int page, int size, String sortBy, String direction) {
        Long tenantId = requireTenant();
        Pageable pageable = buildPageable(page, size, sortBy, direction);
        Page<UserResponse> result = userRepository
                .findAll(UserSpecifications.withFilters(tenantId, keyword, accessType, status), pageable)
                .map(userMapper::toResponse);
        return PagedResponse.from(result);
    }

    @Override
    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User user = findInTenant(id);
        userMapper.updateUserFromRequest(request, user);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        return userMapper.toResponse(userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId)));
    }

    @Override
    @Transactional
    public UserResponse updateProfile(Long userId,
            com.priceintel.backend.dto.request.UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        // Only self-editable fields; email/role are not changed here.
        if (request.getFirstName() != null) user.setFirstName(request.getFirstName());
        if (request.getLastName() != null) user.setLastName(request.getLastName());
        if (request.getPhone() != null) user.setPhone(request.getPhone().isBlank() ? null : request.getPhone());
        if (request.getProfileImage() != null) {
            user.setProfileImage(request.getProfileImage().isBlank() ? null : request.getProfileImage());
        }
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public void softDeleteUser(Long id) {
        User user = findInTenant(id);
        guardSelf(user, "delete your own account");
        user.setStatus(UserStatus.DELETED);
        refreshTokenRepository.deleteByUser(user);
        userRepository.save(user);
        log.info("Soft-deleted user id={}", id);
    }

    @Override
    @Transactional
    public UserResponse activateUser(Long id) {
        User user = findInTenant(id);
        if (user.getStatus() == UserStatus.DELETED) {
            throw new BadRequestException("Cannot activate a deleted user");
        }
        user.setStatus(UserStatus.ACTIVE);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse deactivateUser(Long id) {
        User user = findInTenant(id);
        guardSelf(user, "deactivate your own account");
        user.setStatus(UserStatus.INACTIVE);
        refreshTokenRepository.deleteByUser(user);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public void resetPassword(Long id, ResetUserPasswordRequest request) {
        User user = findInTenant(id);
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        refreshTokenRepository.deleteByUser(user);
        userRepository.save(user);
        log.info("Reset password for user id={}", id);
    }

    // ---------- helpers ----------

    /** Loads a user, enforcing that it belongs to the caller's tenant (isolation). */
    private User findInTenant(Long id) {
        Long tenantId = requireTenant();
        User user = userRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        if (user.getStatus() == UserStatus.DELETED) {
            throw new ResourceNotFoundException("User not found with id: " + id);
        }
        return user;
    }

    private Long requireTenant() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BadRequestException("No tenant context — this operation is for tenant users");
        }
        return tenantId;
    }

    private void guardSelf(User target, String action) {
        SecurityUtils.getCurrentUsername().ifPresent(email -> {
            if (email.equalsIgnoreCase(target.getEmail())) {
                throw new BadRequestException("You cannot " + action);
            }
        });
    }

    private Pageable buildPageable(int page, int size, String sortBy, String direction) {
        if (page < 0) {
            throw new BadRequestException("Page index must not be negative");
        }
        if (size < 1 || size > 100) {
            throw new BadRequestException("Page size must be between 1 and 100");
        }
        String sortField = SORTABLE_FIELDS.contains(sortBy) ? sortBy : "createdAt";
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(dir, sortField));
    }
}

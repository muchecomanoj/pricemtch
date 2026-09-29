package com.priceintel.backend.service.impl;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.constants.AccountAccess;
import com.priceintel.backend.exception.AccountAccessException;
import com.priceintel.backend.dto.request.ChangePasswordRequest;
import com.priceintel.backend.dto.request.ForgotPasswordRequest;
import com.priceintel.backend.dto.request.LoginRequest;
import com.priceintel.backend.dto.request.RefreshTokenRequest;
import com.priceintel.backend.dto.request.ResetPasswordRequest;
import com.priceintel.backend.dto.request.VerifyResetCodeRequest;
import com.priceintel.backend.dto.response.AuthResponse;
import com.priceintel.backend.dto.response.UserResponse;
import com.priceintel.backend.entity.PasswordResetToken;
import com.priceintel.backend.entity.RefreshToken;
import com.priceintel.backend.entity.Tenant;
import com.priceintel.backend.entity.User;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.exception.ResourceNotFoundException;
import com.priceintel.backend.exception.TokenRefreshException;
import com.priceintel.backend.mapper.UserMapper;
import com.priceintel.backend.repository.PasswordResetTokenRepository;
import com.priceintel.backend.repository.RefreshTokenRepository;
import com.priceintel.backend.repository.TenantRepository;
import com.priceintel.backend.repository.UserRepository;
import com.priceintel.backend.security.CustomUserDetails;
import com.priceintel.backend.security.JwtService;
import com.priceintel.backend.service.AuthService;
import com.priceintel.backend.service.MailService;
import com.priceintel.backend.utils.SecurityUtils;

import org.springframework.security.authentication.AuthenticationManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final MailService mailService;
    private final SubscriptionAccessService accessService;

    /** How long an emailed reset code stays valid. */
    private static final int RESET_CODE_TTL_MINUTES = 15;
    /** Wrong-code attempts allowed before the grant is locked. */
    private static final int MAX_RESET_ATTEMPTS = 5;

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Tenant users are blocked if their organization is not usable.
        if (!user.isSuperAdmin()) {
            Tenant tenant = tenantRepository.findById(user.getTenantId())
                    .orElseThrow(() -> AccountAccessException.blocked("Your organization was not found."));
            assertTenantUsable(tenant);
        }

        // Two-factor enabled: hold the session. No tokens are issued until the
        // OTP is verified via POST /auth/2fa/verify with this challenge token.
        if (user.isTwoFactorEnabled()) {
            log.info("Login: {} requires two-factor verification", user.getEmail());
            return AuthResponse.builder()
                    .twoFactorRequired(true)
                    .challengeToken(jwtService.generateTwoFactorChallengeToken(
                            user.getEmail(), request.isRememberMe()))
                    .build();
        }

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        log.info("Login: {} (superAdmin={})", user.getEmail(), user.isSuperAdmin());
        return buildAuthResponse(user, request.isRememberMe());
    }

    /**
     * Refuses sign-in only for a company that is BLOCKED. An expired one gets
     * in, read-only: payment is one-off, so turning expired customers away at
     * the door would leave them no way to pay and come back.
     *
     * <p>Throws AccountAccessException rather than Spring's
     * AccessDeniedException, whose message the global handler replaces with a
     * generic one — which is why a suspended company used to be told only
     * "You do not have permission to access this resource".</p>
     */
    private void assertTenantUsable(Tenant tenant) {
        if (accessService.accessFor(tenant) == AccountAccess.BLOCKED) {
            throw AccountAccessException.blocked(SubscriptionAccessService.blockedReason(tenant));
        }
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String requestToken = request.getRefreshToken();
        RefreshToken stored = refreshTokenRepository.findByToken(requestToken)
                .orElseThrow(() -> new TokenRefreshException("Refresh token not recognised"));
        if (stored.isRevoked()) {
            throw new TokenRefreshException("Refresh token has been revoked");
        }
        if (stored.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(stored);
            throw new TokenRefreshException("Refresh token has expired. Please log in again");
        }
        User user = stored.getUser();
        CustomUserDetails ud = new CustomUserDetails(user);
        if (!jwtService.isTokenValid(requestToken, ud.getUsername())) {
            throw new TokenRefreshException("Refresh token is invalid");
        }
        // Looked at again on every refresh. It was checked only at login, so a
        // company suspended mid-session kept renewing its session indefinitely.
        if (!user.isSuperAdmin()
                && accessService.accessForTenant(user.getTenantId()) == AccountAccess.BLOCKED) {
            refreshTokenRepository.delete(stored);
            throw new TokenRefreshException(SubscriptionAccessService.blockedReason(
                    tenantRepository.findById(user.getTenantId()).orElse(null)));
        }
        // Continue the session; never restart its clock. The replacement token
        // ends when this one would have, so a signed-in user is refreshed
        // silently every 15 minutes but is still signed out when the session's
        // own limit arrives. Before this, each refresh issued a fresh window,
        // so anyone using the app weekly stayed signed in for ever.
        long remainingMs = java.time.Duration.between(Instant.now(), stored.getExpiryDate()).toMillis();
        refreshTokenRepository.delete(stored);
        return buildAuthResponse(user, remainingMs);
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken).ifPresent(refreshTokenRepository::delete);
        log.info("Logout; refresh token invalidated");
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = getAuthenticatedUser();
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BadRequestException("New password must be different from the current password");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        refreshTokenRepository.deleteByUser(user);
        log.info("Password changed for {}", user.getEmail());
    }

    /**
     * Step 1 — email a 6-digit reset code. Any previous unused code for this
     * user is discarded so only the newest one works.
     */
    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No account found with this email address."));
        if (user.getStatus() == com.priceintel.backend.constants.UserStatus.DELETED) {
            throw new ResourceNotFoundException("No account found with this email address.");
        }

        passwordResetTokenRepository.deleteByUser(user);

        String code = String.format("%06d", new java.security.SecureRandom().nextInt(1_000_000));
        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .token(UUID.randomUUID().toString())
                .codeHash(passwordEncoder.encode(code))
                .user(user)
                .expiryDate(Instant.now().plusSeconds(RESET_CODE_TTL_MINUTES * 60L))
                .verified(false).used(false).attempts(0)
                .build());

        mailService.sendPasswordResetCodeEmail(user.getEmail(), user.getFullName(),
                code, RESET_CODE_TTL_MINUTES);
        log.info("Password reset code issued for {}", user.getEmail());
        log.debug("[DEV] Reset code for {} -> {}", user.getEmail(), code);
    }

    /** Step 2 — check the code so the UI can move to the reset-password page. */
    @Override
    @Transactional
    public void verifyResetCode(VerifyResetCodeRequest request) {
        User user = userByEmail(request.getEmail());
        PasswordResetToken grant = activeGrant(user);
        assertCode(grant, request.getCode());
        grant.setVerified(true);
        passwordResetTokenRepository.save(grant);
        log.info("Reset code verified for {}", user.getEmail());
    }

    /** Step 3 — set the new password (BCrypt) and consume the code. */
    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        User user = userByEmail(request.getEmail());
        PasswordResetToken grant = activeGrant(user);
        assertCode(grant, request.getCode());

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        grant.setUsed(true);
        passwordResetTokenRepository.save(grant);
        // Force re-login everywhere with the new password.
        refreshTokenRepository.deleteByUser(user);
        log.info("Password reset completed for {}", user.getEmail());
    }

    private User userByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("No account found with this email address."));
    }

    /** The newest unused, unexpired, unlocked reset grant for the user. */
    private PasswordResetToken activeGrant(User user) {
        PasswordResetToken grant = passwordResetTokenRepository
                .findTopByUserAndUsedFalseOrderByIdDesc(user)
                .orElseThrow(() -> new BadRequestException(
                        "No active reset request. Please request a new code."));
        if (grant.isExpired()) {
            throw new BadRequestException("This code has expired. Please request a new one.");
        }
        if (grant.getAttempts() >= MAX_RESET_ATTEMPTS) {
            throw new BadRequestException("Too many incorrect attempts. Please request a new code.");
        }
        return grant;
    }

    /** Verifies the code, counting failures to prevent brute force. */
    private void assertCode(PasswordResetToken grant, String code) {
        if (grant.getCodeHash() == null || !passwordEncoder.matches(code, grant.getCodeHash())) {
            grant.setAttempts(grant.getAttempts() + 1);
            passwordResetTokenRepository.save(grant);
            int left = Math.max(0, MAX_RESET_ATTEMPTS - grant.getAttempts());
            throw new BadRequestException("Invalid verification code. " + left + " attempt(s) remaining.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        User user = getAuthenticatedUser();
        UserResponse response = userMapper.toResponse(user);
        // Read fresh, not from the request cache: this is what the banner shows.
        response.setAccount(accessService.describeTenant(user.getTenantId()));
        return response;
    }

    // ---------- helpers ----------

    /** A new session: its whole length is decided here, at login. */
    private AuthResponse buildAuthResponse(User user, boolean rememberMe) {
        return buildAuthResponse(user, jwtService.getRefreshTokenExpirationMs(rememberMe));
    }

    private AuthResponse buildAuthResponse(User user, long refreshTtlMs) {
        CustomUserDetails ud = new CustomUserDetails(user);
        String accessToken = jwtService.generateAccessToken(ud);
        String refreshToken = jwtService.generateRefreshToken(ud, refreshTtlMs);
        refreshTokenRepository.save(RefreshToken.builder()
                .token(refreshToken).user(user)
                .expiryDate(Instant.now().plusMillis(refreshTtlMs))
                .revoked(false).build());
        UserResponse userResponse = userMapper.toResponse(user);
        userResponse.setAccount(accessService.describeTenant(user.getTenantId()));
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtService.getAccessTokenExpirationSeconds())
                .user(userResponse)
                .build();
    }

    private User getAuthenticatedUser() {
        String email = SecurityUtils.getCurrentUsername()
                .orElseThrow(() -> new BadRequestException("No authenticated user in context"));
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }
}

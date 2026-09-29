package com.priceintel.backend.security;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.priceintel.backend.dto.response.AuthResponse;
import com.priceintel.backend.entity.RefreshToken;
import com.priceintel.backend.entity.User;
import com.priceintel.backend.mapper.UserMapper;
import com.priceintel.backend.repository.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;

/**
 * Issues a full authenticated session (access + refresh tokens) for a user.
 * Shared by the normal login flow and the two-factor verification flow so both
 * produce an identical session payload.
 */
@Component
@RequiredArgsConstructor
public class TokenIssuer {

    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserMapper userMapper;
    private final com.priceintel.backend.service.impl.SubscriptionAccessService accessService;

    public AuthResponse issue(User user, boolean rememberMe) {
        CustomUserDetails userDetails = new CustomUserDetails(user);
        String accessToken = jwtService.generateAccessToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails, rememberMe);

        refreshTokenRepository.save(RefreshToken.builder()
                .token(refreshToken)
                .user(user)
                .expiryDate(Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs(rememberMe)))
                .revoked(false)
                .build());

        // Same standing as a password-only login, so a two-factor user sees the
        // same expiry banner.
        com.priceintel.backend.dto.response.UserResponse userResponse = userMapper.toResponse(user);
        userResponse.setAccount(accessService.describeTenant(user.getTenantId()));
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtService.getAccessTokenExpirationSeconds())
                .user(userResponse)
                .build();
    }
}

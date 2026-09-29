package com.priceintel.backend.service.impl;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.dto.response.AuthResponse;
import com.priceintel.backend.dto.response.TwoFactorEnableResponse;
import com.priceintel.backend.dto.response.TwoFactorSetupResponse;
import com.priceintel.backend.dto.response.TwoFactorStatusResponse;
import com.priceintel.backend.entity.User;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.exception.ResourceNotFoundException;
import com.priceintel.backend.repository.UserRepository;
import com.priceintel.backend.security.JwtService;
import com.priceintel.backend.security.TokenIssuer;
import com.priceintel.backend.security.TotpService;
import com.priceintel.backend.service.TwoFactorService;
import com.priceintel.backend.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TwoFactorServiceImpl implements TwoFactorService {

    private final UserRepository userRepository;
    private final TotpService totpService;
    private final JwtService jwtService;
    private final TokenIssuer tokenIssuer;
    private final PasswordEncoder passwordEncoder;

    private static final int BACKUP_CODE_COUNT = 8;

    @Override
    @Transactional(readOnly = true)
    public TwoFactorStatusResponse status() {
        return new TwoFactorStatusResponse(currentUser().isTwoFactorEnabled());
    }

    @Override
    @Transactional
    public TwoFactorSetupResponse setup() {
        User user = currentUser();
        if (user.isTwoFactorEnabled()) {
            throw new BadRequestException("Two-factor authentication is already enabled");
        }
        // A fresh secret each time setup is called; it only becomes active on enable().
        String secret = totpService.generateSecret();
        user.setTwoFactorSecret(secret);
        userRepository.save(user);

        log.info("2FA enrollment started for {}", user.getEmail());
        return TwoFactorSetupResponse.builder()
                .secret(secret)
                .otpauthUrl(totpService.buildOtpAuthUrl(user.getEmail(), secret))
                .build();
    }

    @Override
    @Transactional
    public TwoFactorEnableResponse enable(String code) {
        User user = currentUser();
        if (user.isTwoFactorEnabled()) {
            throw new BadRequestException("Two-factor authentication is already enabled");
        }
        if (user.getTwoFactorSecret() == null) {
            throw new BadRequestException("Call /auth/2fa/setup before enabling");
        }
        if (!totpService.verifyCode(user.getTwoFactorSecret(), code)) {
            throw new BadRequestException("Invalid code. Check your authenticator app and try again.");
        }

        List<String> plainCodes = generateBackupCodes();
        user.setBackupCodes(plainCodes.stream()
                .map(passwordEncoder::encode)
                .collect(Collectors.joining(",")));
        user.setTwoFactorEnabled(true);
        userRepository.save(user);

        log.info("2FA enabled for {}", user.getEmail());
        return TwoFactorEnableResponse.builder().enabled(true).backupCodes(plainCodes).build();
    }

    @Override
    @Transactional
    public TwoFactorStatusResponse disable(String code) {
        User user = currentUser();
        if (!user.isTwoFactorEnabled()) {
            return new TwoFactorStatusResponse(false);
        }
        if (!matchesTotpOrBackupCode(user, code)) {
            throw new BadRequestException("Invalid code. Two-factor authentication was not disabled.");
        }
        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null);
        user.setBackupCodes(null);
        userRepository.save(user);

        log.info("2FA disabled for {}", user.getEmail());
        return new TwoFactorStatusResponse(false);
    }

    @Override
    @Transactional
    public AuthResponse verify(String code, String challengeToken) {
        User user = userFromChallenge(challengeToken);
        if (!user.isTwoFactorEnabled()) {
            throw new BadRequestException("Two-factor authentication is not enabled for this account");
        }
        if (!matchesTotpOrBackupCode(user, code)) {
            throw new BadRequestException("Invalid verification code");
        }
        boolean rememberMe = jwtService.extractRememberMe(challengeToken);
        // Login only truly completes here, so stamp lastLogin now. This also
        // persists a consumed backup code, if one was used.
        user.setLastLogin(java.time.LocalDateTime.now());
        userRepository.save(user);

        log.info("2FA verified; issuing session for {}", user.getEmail());
        return tokenIssuer.issue(user, rememberMe);
    }

    @Override
    @Transactional(readOnly = true)
    public void resend(String challengeToken) {
        // TOTP codes are generated on the user's device — nothing is sent. We
        // simply confirm the challenge is still valid so the UI can reset its timer.
        userFromChallenge(challengeToken);
    }

    // ---------- helpers ----------

    /** Validates the challenge token and loads its user. */
    private User userFromChallenge(String challengeToken) {
        String email;
        try {
            if (!JwtService.TOKEN_TYPE_2FA_CHALLENGE.equals(jwtService.extractTokenType(challengeToken))) {
                throw new BadRequestException("Invalid challenge token");
            }
            email = jwtService.extractUsername(challengeToken);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Challenge expired or invalid. Please log in again.");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    /** Accepts a TOTP code, or consumes a one-time backup code. */
    private boolean matchesTotpOrBackupCode(User user, String code) {
        if (totpService.verifyCode(user.getTwoFactorSecret(), code)) {
            return true;
        }
        return consumeBackupCode(user, code);
    }

    /** Backup codes are single-use: a match removes it from the stored set. */
    private boolean consumeBackupCode(User user, String code) {
        if (user.getBackupCodes() == null || user.getBackupCodes().isBlank()) {
            return false;
        }
        List<String> hashes = new ArrayList<>(Arrays.asList(user.getBackupCodes().split(",")));
        for (int i = 0; i < hashes.size(); i++) {
            if (passwordEncoder.matches(code, hashes.get(i))) {
                hashes.remove(i);
                user.setBackupCodes(String.join(",", hashes));
                log.info("Backup code consumed for {} ({} remaining)", user.getEmail(), hashes.size());
                return true;
            }
        }
        return false;
    }

    private List<String> generateBackupCodes() {
        SecureRandom random = new SecureRandom();
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            codes.add(String.format("%04d-%04d", random.nextInt(10000), random.nextInt(10000)));
        }
        return codes;
    }

    private User currentUser() {
        String email = SecurityUtils.getCurrentUsername()
                .orElseThrow(() -> new BadRequestException("No authenticated user in context"));
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }
}

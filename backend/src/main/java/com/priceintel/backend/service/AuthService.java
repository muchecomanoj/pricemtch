package com.priceintel.backend.service;

import com.priceintel.backend.dto.request.ChangePasswordRequest;
import com.priceintel.backend.dto.request.ForgotPasswordRequest;
import com.priceintel.backend.dto.request.LoginRequest;
import com.priceintel.backend.dto.request.RefreshTokenRequest;
import com.priceintel.backend.dto.request.ResetPasswordRequest;
import com.priceintel.backend.dto.request.VerifyResetCodeRequest;
import com.priceintel.backend.dto.response.AuthResponse;
import com.priceintel.backend.dto.response.UserResponse;

/**
 * Authentication use cases shared by SUPER_ADMIN, Clients, and tenant Users.
 */
public interface AuthService {

    AuthResponse login(LoginRequest request);

    AuthResponse refreshToken(RefreshTokenRequest request);

    void logout(String refreshToken);

    void changePassword(ChangePasswordRequest request);

    /** Step 1 — emails a 6-digit reset code to the registered address. */
    void forgotPassword(ForgotPasswordRequest request);

    /** Step 2 — validates the emailed code so the user may proceed to reset. */
    void verifyResetCode(VerifyResetCodeRequest request);

    /** Step 3 — sets the new password (BCrypt) and invalidates the code. */
    void resetPassword(ResetPasswordRequest request);

    UserResponse getCurrentUser();
}

package com.priceintel.backend.dto.request;

import com.priceintel.backend.validation.ValidPassword;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** An admin resetting another user's password. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetUserPasswordRequest {

    @NotBlank(message = "New password is required")
    @ValidPassword
    private String newPassword;
}

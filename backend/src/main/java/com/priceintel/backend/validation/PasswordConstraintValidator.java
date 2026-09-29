package com.priceintel.backend.validation;

import java.util.regex.Pattern;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Enforces the password rules declared by {@link ValidPassword}.
 */
public class PasswordConstraintValidator implements ConstraintValidator<ValidPassword, String> {

    // At least: 1 lowercase, 1 uppercase, 1 digit, 1 special char, min length 8.
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).{8,}$");

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return false;
        }
        return PASSWORD_PATTERN.matcher(password).matches();
    }
}

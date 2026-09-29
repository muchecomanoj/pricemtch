package com.priceintel.backend.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Custom Bean Validation annotation enforcing a strong password policy.
 * Usage: annotate a String field with {@code @ValidPassword}.
 */
@Documented
@Constraint(validatedBy = PasswordConstraintValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

    String message() default "Password must be at least 8 characters and include an uppercase letter, "
            + "a lowercase letter, a digit, and a special character";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

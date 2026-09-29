package com.priceintel.backend.entity;

import com.priceintel.backend.constants.AccessType;
import com.priceintel.backend.constants.UserStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A platform user. Two kinds exist:
 * <ul>
 *   <li><b>SUPER_ADMIN</b> — {@code superAdmin=true}, {@code tenantId=null},
 *       {@code accessType=null}: the single platform owner.</li>
 *   <li><b>Tenant user</b> — belongs to exactly one tenant and has an
 *       {@link AccessType}. A tenant user with {@code ADMIN} access type is that
 *       organization's Tenant Administrator (the "Client").</li>
 * </ul>
 * There is no role table; access for tenant users is driven by {@code accessType}.
 */
@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_email", columnNames = "email")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    /** Null for the SUPER_ADMIN; otherwise the owning tenant. */
    @Column(name = "tenant_id")
    private Long tenantId;

    @Builder.Default
    @Column(name = "super_admin", nullable = false)
    private boolean superAdmin = false;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(nullable = false)
    private String password;

    /** Null for SUPER_ADMIN; required for tenant users. */
    @Enumerated(EnumType.STRING)
    @Column(name = "access_type", length = 20)
    private AccessType accessType;

    /** URL or base64 data: URI (direct upload) — TEXT, no length cap. */
    @Column(name = "profile_image", columnDefinition = "TEXT")
    private String profileImage;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    // ---- Two-factor authentication (TOTP) ----

    @Builder.Default
    @Column(name = "two_factor_enabled", nullable = false)
    private boolean twoFactorEnabled = false;

    /** Base32 TOTP shared secret. Present once enrollment starts. */
    @Column(name = "two_factor_secret", length = 64)
    private String twoFactorSecret;

    /** BCrypt hashes of one-time backup codes, comma-separated. */
    @Column(name = "backup_codes", length = 2000)
    private String backupCodes;

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public boolean isDeleted() {
        return status == UserStatus.DELETED;
    }
}

package com.priceintel.backend.entity;

import java.time.Instant;

import com.priceintel.backend.constants.MarketplaceProvider;

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

/**
 * Platform-global configuration for a marketplace provider. Credentials are held
 * as an AES-encrypted JSON blob — never stored or returned in plaintext.
 */
@Entity
@Table(name = "marketplace_configs", uniqueConstraints = {
        @UniqueConstraint(name = "uk_marketplace_provider", columnNames = "provider")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketplaceConfig extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MarketplaceProvider provider;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = false;

    /** AES-encrypted JSON map of credential key -> value. */
    @Column(name = "encrypted_credentials", length = 4000)
    private String encryptedCredentials;

    /** NOT_CONFIGURED | CONNECTED | ERROR. */
    @Builder.Default
    @Column(length = 20)
    private String status = "NOT_CONFIGURED";

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "last_message", length = 500)
    private String lastMessage;
}

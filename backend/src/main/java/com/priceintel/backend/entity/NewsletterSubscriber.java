package com.priceintel.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An address on the newsletter list.
 *
 * <p>Leaving the list clears {@code active} rather than deleting the row, so a
 * later re-subscribe cannot silently resurrect someone who asked to be left
 * alone — and so the same address is never counted twice.</p>
 */
@Entity
@Table(name = "newsletter_subscribers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsletterSubscriber extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String email;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Authorises unsubscribing. Held only by whoever received the email, because
     * an address alone is public and would let anyone remove anyone.
     */
    @Column(name = "unsubscribe_token", nullable = false, length = 64)
    private String unsubscribeToken;
}

package com.priceintel.backend.entity;

import java.time.Instant;

import com.priceintel.backend.constants.NotificationType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An in-app notification delivered to a single user (the bell icon).
 */
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notification_recipient", columnList = "recipient_user_id, read")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification extends BaseEntity {

    /** The user who receives this notification. */
    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    /** The tenant this relates to (null for platform-level). */
    @Column(name = "tenant_id")
    private Long tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 500)
    private String message;

    /** Optional in-app link, e.g. "/client/subscription". */
    @Column(length = 200)
    private String link;

    @Builder.Default
    @Column(nullable = false)
    private boolean read = false;

    @Column(name = "read_at")
    private Instant readAt;
}

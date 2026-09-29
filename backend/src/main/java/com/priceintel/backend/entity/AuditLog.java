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
 * An audit trail entry. Records who did what, on which entity, in which tenant.
 * Powers "View Audit Logs".
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog extends BaseEntity {

    /** Actor email (or SYSTEM). */
    @Column(nullable = false, length = 150)
    private String actor;

    /** Tenant the action related to (null for platform-level actions). */
    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", length = 60)
    private String entityType;

    @Column(name = "entity_id", length = 60)
    private String entityId;

    @Column(length = 1000)
    private String details;
}

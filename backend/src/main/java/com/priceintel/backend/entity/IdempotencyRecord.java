package com.priceintel.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One recorded action, so repeating it returns the first answer. */
@Entity
@Table(name = "idempotency_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyRecord extends BaseEntity {

    @Column(name = "idem_key", nullable = false, length = 200)
    private String idemKey;

    @Column(nullable = false, length = 80)
    private String scope;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "response_json", columnDefinition = "text")
    private String responseJson;

    /** IN_PROGRESS while the first call runs, then COMPLETED or FAILED. */
    @Builder.Default
    @Column(nullable = false, length = 20)
    private String status = "IN_PROGRESS";
}

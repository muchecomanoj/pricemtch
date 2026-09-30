package com.priceintel.backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Somebody asked for a demo from the landing page.
 *
 * <p>Not tenant-scoped: whoever fills this in has no account yet, which is the
 * whole point of the form.</p>
 */
@Entity
@Table(name = "demo_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemoRequest extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(length = 30)
    private String phone;

    @Column(length = 1000)
    private String message;

    /** Someone has replied. Stops a lead being answered twice, or not at all. */
    @Builder.Default
    @Column(nullable = false)
    private boolean handled = false;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    @Column(name = "handled_by", length = 150)
    private String handledBy;
}

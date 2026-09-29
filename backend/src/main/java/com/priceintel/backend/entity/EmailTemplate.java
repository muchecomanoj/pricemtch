package com.priceintel.backend.entity;

import com.priceintel.backend.constants.EmailTemplateKey;

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
 * A super-admin-editable email template (subject + body). One row per
 * {@link EmailTemplateKey}. When a template is unedited, the enum default is used.
 */
@Entity
@Table(name = "email_templates", uniqueConstraints = {
        @UniqueConstraint(name = "uk_email_template_key", columnNames = "template_key")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailTemplate extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "template_key", nullable = false, length = 40)
    private EmailTemplateKey templateKey;

    @Column(nullable = false, length = 300)
    private String subject;

    @Column(nullable = false, length = 8000)
    private String body;

    /** True once a super admin has edited it (vs the seeded default). */
    @Builder.Default
    @Column(nullable = false)
    private boolean edited = false;
}

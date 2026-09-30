package com.priceintel.backend.entity;

import com.priceintel.backend.constants.LandingSection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One section of the public landing page, as JSON.
 *
 * <p>The shape of {@code payload} differs per section and is not validated by
 * the database — {@code LandingContentService} checks it parses, and the page
 * is responsible for rendering what it finds. A malformed edit would otherwise
 * take the marketing site down, which is why nothing here is ever required for
 * the application itself to work.</p>
 */
@Entity
@Table(name = "landing_content")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LandingContent extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, unique = true)
    private LandingSection section;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    /** Hides a section without losing what was written in it. */
    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}

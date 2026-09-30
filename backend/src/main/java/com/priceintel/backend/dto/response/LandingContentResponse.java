package com.priceintel.backend.dto.response;

import java.time.LocalDateTime;

import com.priceintel.backend.constants.LandingSection;
import com.priceintel.backend.entity.LandingContent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One landing-page section, for the Super Admin's editor. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LandingContentResponse {

    private LandingSection section;

    /** The JSON the page renders, as text, exactly as it is stored. */
    private String payload;

    private boolean active;
    private LocalDateTime updatedAt;
    private String updatedBy;

    public static LandingContentResponse from(LandingContent c) {
        return LandingContentResponse.builder()
                .section(c.getSection())
                .payload(c.getPayload())
                .active(c.isActive())
                .updatedAt(c.getUpdatedAt())
                .updatedBy(c.getUpdatedBy())
                .build();
    }
}

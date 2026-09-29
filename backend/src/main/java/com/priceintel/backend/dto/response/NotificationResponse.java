package com.priceintel.backend.dto.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One in-app notification for the bell dropdown. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private Long id;
    private String type;
    private String title;
    private String message;
    private String link;
    private boolean read;
    private LocalDateTime createdAt;
}

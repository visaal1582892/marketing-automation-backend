package com.medplus.marketing_automation_backend.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTemplate {

    private Long          id;
    private String        eventType;
    /** Null means this is the default template (applies when no role-specific match exists). */
    private String        roleId;
    private String        messageTemplate;
    private String        urlTemplate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

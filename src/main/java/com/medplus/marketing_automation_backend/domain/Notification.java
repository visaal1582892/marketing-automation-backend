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
public class Notification {

    private Long          id;
    private Long          userId;
    private String        eventType;
    private String        message;
    private String        url;
    private boolean       read;
    private LocalDateTime createdAt;
}

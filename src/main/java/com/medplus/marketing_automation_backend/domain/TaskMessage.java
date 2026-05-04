package com.medplus.marketing_automation_backend.domain;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskMessage {
    private Integer       messageId;
    private String        taskId;
    private Integer       userId;
    private String        userName;
    private String        message;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

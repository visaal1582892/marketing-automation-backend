package com.medplus.marketing_automation_backend.domain;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerComment {
    private Integer       commentId;
    private String        taskId;
    private Integer       userId;
    private String        userName;
    private String        comment;
    private boolean       answered;
    private LocalDateTime createdAt;
}

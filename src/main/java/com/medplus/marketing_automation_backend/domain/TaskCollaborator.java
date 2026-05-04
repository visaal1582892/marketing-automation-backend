package com.medplus.marketing_automation_backend.domain;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskCollaborator {
    private Integer       id;
    private String        taskId;
    private Integer       userId;
    private String        userName;
    private String        userEmail;
    private String        designationName;
    private LocalDateTime addedAt;
}

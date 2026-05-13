package com.medplus.marketing_automation_backend.domain;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoCreatedTask {

    private Long          autoCreatedTaskId;
    private String        sourceTaskId;
    private String        createdTaskId;
    private Integer       campaignId;
    private String        sourceGranularTaskId;
    private String        contentGranularTaskId;
    private Integer       requestedByUserId;
    private Integer       contentAssigneeUserId;
    private String        status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

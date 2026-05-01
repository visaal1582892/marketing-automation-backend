package com.medplus.marketing_automation_backend.domain;

import com.medplus.marketing_automation_backend.enums.ApprovalAction;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalLog {

    private Integer        logId;
    /** FK to work_tasks.task_id (String format: WORK-TASK-X) */
    private String         taskId;
    private Integer        reviewerId;
    private String         reviewerName;
    private ApprovalAction actionTaken;
    private String         comments;
    private LocalDateTime  createdAt;
}

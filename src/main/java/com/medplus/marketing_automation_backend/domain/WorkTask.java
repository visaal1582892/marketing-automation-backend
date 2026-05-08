package com.medplus.marketing_automation_backend.domain;

import com.medplus.marketing_automation_backend.enums.CampaignStatus;
import com.medplus.marketing_automation_backend.enums.Priority;
import com.medplus.marketing_automation_backend.enums.TaskStatus;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkTask {

    /** Custom string PK: WORK-TASK-1, WORK-TASK-2, … */
    private String     taskId;
    private Integer    campaignId;
    private Integer    assignedTo;
    private Integer    requestorId;
    private String     assigneeName;
    private String     granularTaskId;
    private String     granularTaskName;
    /** Resolved via granular_tasks → task_types join */
    private String     taskTypeName;
    private TaskStatus status;

    // Lifecycle timestamps (Module 3 — auditing & efficiency reports)
    //   assignedAt  : task was created and assigned to this user
    //   acceptedAt  : creator clicked "Accept" — timer starts here
    //   startedAt   : alias of acceptedAt (kept for backward compat)
    //   submittedAt : creator clicked "Submit for QC"
    //   completedAt : QC manager approved and marked the task COMPLETED
    private LocalDateTime assignedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;
    private LocalDateTime completedAt;
    private Integer       totalTimeLoggedMinutes;
    private LocalDateTime dynamicDeadline;

    // Submitted by the creator when marking the task complete (Module 3)
    private String        submissionNotes;

    /** Number of times the QC reviewer (marketing manager) has sent this task back for rework. */
    private Integer       reworkCount;

    /** Number of times the requestor has sent this task back for rework after it was COMPLETED. */
    private Integer       requestorReworkCount;

    /** Latest comment from the marketing manager (NEEDS_REWORK) for this task. */
    private String        latestManagerReworkComment;

    /** Latest comment from the requestor (REQUESTOR_REWORK) for this task. */
    private String        latestRequestorReworkComment;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** True once the worker has clicked "Collaborate" — never reverts to false. */
    private boolean collaborationStarted;

    /**
     * True when the collaboration is currently open for chat and asset uploads.
     * Set to false automatically when the task enters HELD, QC_REVIEW, or COMPLETED,
     * and can also be toggled manually by the owner (Pause Chat).
     */
    private boolean collaborationActive;

    // Campaign context (populated for employee-facing views)
    private LocalDate      campaignDeadline;
    private Priority       campaignPriority;
    private String         requestorName;
    /** Name of whoever performed the most recent approvals_log action; null for old tasks with no log. */
    private String         latestActionDoneByName;
    private CampaignStatus campaignStatus;
}

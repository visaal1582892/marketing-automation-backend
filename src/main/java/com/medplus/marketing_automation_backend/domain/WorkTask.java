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
    private String        assetUrl;

    /**
     * Comment left by the worker when they self-hold the task (e.g. a blocker
     * or clarification question for the requestor). Cleared when the task is
     * un-held.
     */
    private String        workerComment;

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

    // Campaign context (populated for employee-facing views)
    private LocalDate      campaignDeadline;
    private Priority       campaignPriority;
    private String         requirementTypeName;
    private String         requestorName;
    private CampaignStatus campaignStatus;
}

package com.medplus.marketing_automation_backend.dto;

import com.medplus.marketing_automation_backend.domain.WorkerComment;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkTaskResponse {

    /** Custom string PK: WORK-TASK-1, WORK-TASK-2, … */
    private String        taskId;
    private Integer       campaignId;
    private String        requirementTypeName;
    private String        requestorName;
    private String        granularTaskId;
    private String        granularTaskName;
    private String        taskTypeName;
    private Integer       assignedTo;
    private Integer       requestorId;
    private String        assigneeName;
    private String        status;
    private LocalDate     campaignDeadline;
    private String        campaignPriority;
    /**
     * Status of the parent campaign — exposed so the UI can grey-out / hide
     * tasks whose campaign has been rejected or completed (those tasks are no
     * longer actionable even if the task row itself wasn't auto-cancelled in
     * time).
     */
    private String        campaignStatus;
    private LocalDateTime assignedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;
    private LocalDateTime completedAt;
    private Integer       totalTimeLoggedMinutes;
    private LocalDateTime dynamicDeadline;
    private String        submissionNotes;

    /**
     * Active (unanswered) worker comments on this task.
     * Replaces the old single workerComment column — now a proper list.
     */
    private List<WorkerComment> activeComments;

    private LocalDateTime createdAt;

    /**
     * Number of times this task has been sent back for rework by the QC reviewer (marketing manager).
     * Derived from approvals_log at query time — no extra round-trip needed.
     */
    private Integer       reworkCount;

    /**
     * Number of times the requestor has sent this task back for rework after it was COMPLETED.
     * Tracked separately from the manager rework count.
     */
    private Integer       requestorReworkCount;

    /**
     * Latest comment from the marketing manager (NEEDS_REWORK action).
     * Shown in orange on the worker's task card when status is REWORK.
     */
    private String        latestManagerReworkComment;

    /**
     * Latest comment from the requestor (REQUESTOR_REWORK action).
     * Shown in purple on the worker's task card when status is REWORK.
     */
    private String        latestRequestorReworkComment;

    /** Requestor/worker answers for mapped dynamic questions (campaign brief). */
    private List<WorkTaskQuestionnaireBriefItem> questionnaire;

    /**
     * Role of the requesting user relative to this task in a collaboration context.
     * "OWNER" = the assigned worker who invited collaborators.
     * "COLLABORATOR" = a user who was invited to collaborate.
     * "REQUESTOR" = the campaign requestor (auto-added as collaborator).
     * "ADMIN" = admin user who can see all open collaborations.
     * Null when not used in a collaboration response.
     */
    private String myRole;
}

package com.medplus.marketing_automation_backend.service;

import com.medplus.marketing_automation_backend.domain.WorkTask;
import com.medplus.marketing_automation_backend.dto.PagedResponse;
import com.medplus.marketing_automation_backend.dto.WorkTaskResponse;
import com.medplus.marketing_automation_backend.enums.CampaignStatus;
import com.medplus.marketing_automation_backend.enums.TaskStatus;
import com.medplus.marketing_automation_backend.exception.BadRequestException;
import com.medplus.marketing_automation_backend.exception.ResourceNotFoundException;
import com.medplus.marketing_automation_backend.domain.ApprovalLog;
import com.medplus.marketing_automation_backend.enums.ApprovalAction;
import com.medplus.marketing_automation_backend.repository.ApprovalLogRepository;
import com.medplus.marketing_automation_backend.repository.WorkTaskRepository;
import com.medplus.marketing_automation_backend.repository.WorkerCommentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;



@Service
public class WorkTaskService {

    private final WorkTaskRepository      workTaskRepo;
    private final WorkerCommentRepository workerCommentRepo;
    private final ApprovalLogRepository   approvalLogRepo;

    public WorkTaskService(WorkTaskRepository workTaskRepo,
                           WorkerCommentRepository workerCommentRepo,
                           ApprovalLogRepository approvalLogRepo) {
        this.workTaskRepo      = workTaskRepo;
        this.workerCommentRepo = workerCommentRepo;
        this.approvalLogRepo   = approvalLogRepo;
    }

    // -------------------------------------------------------------------------
    // Employee-facing
    // -------------------------------------------------------------------------

    /** Returns all tasks assigned to the given user, enriched with active comments. */
    public List<WorkTaskResponse> listMy(int userId) {
        return workTaskRepo.findByAssignedTo(userId).stream()
                .map(t -> enrichWithComments(CampaignService.toWorkTaskResponse(t), t.getTaskId()))
                .collect(Collectors.toList());
    }

    public WorkTaskResponse get(String taskId, int userId) {
        WorkTask task = findAndAuthorize(taskId, userId);
        return enrichWithComments(CampaignService.toWorkTaskResponse(task), taskId);
    }

    private WorkTaskResponse enrichWithComments(WorkTaskResponse r, String taskId) {
        r.setActiveComments(workerCommentRepo.findActiveByTaskId(taskId));
        return r;
    }

    /**
     * Accepts a task: sets status → IN_PROGRESS and records the start time.
     * Task must be in ASSIGNED or REWORK state.
     */
    @Transactional
    public WorkTaskResponse accept(String taskId, int userId) {
        WorkTask task = findAndAuthorize(taskId, userId);

        if (task.getStatus() != TaskStatus.ASSIGNED && task.getStatus() != TaskStatus.REWORK) {
            throw new BadRequestException("Task must be ASSIGNED or REWORK to accept. Current status: " + task.getStatus());
        }
        // Refuse to start work on a task whose parent campaign is already
        // closed (rejected/completed). Without this guard, a worker could
        // accept a stale task on a rejected campaign — they'd burn time on
        // something that will never be QC'd.
        if (task.getCampaignStatus() == CampaignStatus.REJECTED
                || task.getCampaignStatus() == CampaignStatus.COMPLETED) {
            throw new BadRequestException(
                    "This task's campaign is already " + task.getCampaignStatus() + " — cannot accept.");
        }
        int updated = workTaskRepo.accept(taskId, userId);
        if (updated == 0) {
            throw new BadRequestException("Unable to accept task — please refresh and try again.");
        }
        // Rule 3: if collaboration was already started, activate it now that the task is IN_PROGRESS
        workTaskRepo.activateCollaboration(taskId);
        return CampaignService.toWorkTaskResponse(
                workTaskRepo.findById(taskId).orElseThrow());
    }

    /**
     * Marks a task complete: sets status → QC_REVIEW, logs time spent, and
     * stores any submission notes / asset URL provided by the creator.
     * Task must be IN_PROGRESS.
     */
    @Transactional
    public WorkTaskResponse complete(String taskId, int userId,
                                     String submissionNotes, java.util.List<String> assetUrls) {
        // assetUrls is intentionally ignored — assets are now managed exclusively
        // via the collaboration panel (asset_info table, not work_tasks)
        WorkTask task = findAndAuthorize(taskId, userId);

        if (task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw new BadRequestException("Task must be IN_PROGRESS to complete. Current status: " + task.getStatus());
        }
        // Same defensive check as accept() — don't accept a QC submission for
        // a task whose campaign was rejected mid-flight.
        if (task.getCampaignStatus() == CampaignStatus.REJECTED
                || task.getCampaignStatus() == CampaignStatus.COMPLETED) {
            throw new BadRequestException(
                    "This task's campaign is already " + task.getCampaignStatus()
                            + " — cannot submit for QC.");
        }

        LocalDateTime now = LocalDateTime.now();
        int minutes = 0;
        if (task.getStartedAt() != null) {
            minutes = (int) Duration.between(task.getStartedAt(), now).toMinutes();
        }
        // Add to any previously logged time (handles rework cycles).
        int previous = task.getTotalTimeLoggedMinutes() == null ? 0 : task.getTotalTimeLoggedMinutes();
        int totalMinutes = previous + Math.max(0, minutes);

        int updated = workTaskRepo.complete(taskId, userId, now, totalMinutes, trim(submissionNotes));
        if (updated == 0) {
            throw new BadRequestException("Unable to complete task — please refresh and try again.");
        }
        // Rule 5: task is now QC_REVIEW — deactivate collaboration
        workTaskRepo.deactivateCollaboration(taskId);
        return CampaignService.toWorkTaskResponse(
                workTaskRepo.findById(taskId).orElseThrow());
    }

    /**
     * Worker adds a comment on their task and self-holds it (status → HELD).
     * This signals a blocker or question to the requestor. The task will stay
     * on hold until the worker resumes it themselves via {@link #workerUnhold}.
     */
    @Transactional
    public WorkTaskResponse addCommentAndHold(String taskId, int userId, String comment) {
        findAndAuthorize(taskId, userId);
        if (comment == null || comment.isBlank()) {
            throw new BadRequestException("Comment must not be blank.");
        }
        // First insert the comment, then flip the task to HELD
        workerCommentRepo.insert(taskId, userId, trim(comment));
        int updated = workTaskRepo.holdTask(taskId, userId);
        if (updated == 0) {
            throw new BadRequestException(
                    "Task cannot be held at its current status. Only ASSIGNED, IN_PROGRESS, or REWORK tasks can be held.");
        }
        approvalLogRepo.insert(ApprovalLog.builder()
                .taskId(taskId).reviewerId(userId)
                .actionTaken(ApprovalAction.HELD).comments(comment).build());
        // Rule 4: task is now HELD — deactivate collaboration
        workTaskRepo.deactivateCollaboration(taskId);
        return CampaignService.toWorkTaskResponse(
                workTaskRepo.findById(taskId).orElseThrow());
    }

    /**
     * Worker resumes the task — marks all active comments answered and restores
     * the task to its pre-hold status.
     */
    @Transactional
    public WorkTaskResponse workerUnhold(String taskId, int userId) {
        findAndAuthorize(taskId, userId);
        workerCommentRepo.markAllAnswered(taskId);
        int updated = workTaskRepo.clearHold(taskId, userId);
        if (updated == 0) {
            throw new BadRequestException(
                    "Task is not self-held or you are not the assigned worker.");
        }
        approvalLogRepo.insert(ApprovalLog.builder()
                .taskId(taskId).reviewerId(userId)
                .actionTaken(ApprovalAction.UNHOLD).build());
        // Re-activate collaboration if the task is restored to IN_PROGRESS
        WorkTask restored = workTaskRepo.findById(taskId).orElseThrow();
        if (restored.getStatus() == TaskStatus.IN_PROGRESS) {
            workTaskRepo.activateCollaboration(taskId);
        }
        return CampaignService.toWorkTaskResponse(
                workTaskRepo.findById(taskId).orElseThrow());
    }

    /** Mark a single worker comment as answered. Any participant can do this. */
    @Transactional
    public void markCommentAnswered(String taskId, int commentId) {
        workerCommentRepo.markAnswered(commentId);
    }

    private static String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    // -------------------------------------------------------------------------
    // Manager-facing
    // -------------------------------------------------------------------------

    /** All tasks across the system — for Marketing Head overview. */
    public List<WorkTaskResponse> listAll() {
        return workTaskRepo.findAll().stream()
                .map(CampaignService::toWorkTaskResponse)
                .collect(Collectors.toList());
    }

    /** Paginated + filtered all-tasks list for the manager's overview table. */
    public PagedResponse<WorkTaskResponse> listAllPaged(
            String taskId, String campaignId,
            String requestorName, String assigneeName,
            String taskType, String priority, String status,
            LocalDate dateFrom, LocalDate dateTo,
            int page, int size) {

        PagedResponse<WorkTask> raw = workTaskRepo.findAllPaged(
                taskId, campaignId, requestorName, assigneeName,
                taskType, priority, status, dateFrom, dateTo, page, size);

        List<WorkTaskResponse> mapped = raw.content().stream()
                .map(CampaignService::toWorkTaskResponse)
                .collect(Collectors.toList());
        return PagedResponse.of(mapped, raw.totalElements(), raw.page(), raw.size());
    }

    public List<WorkTaskResponse> listPendingQcReview() {
        return workTaskRepo.findPendingQcReview().stream()
                .map(CampaignService::toWorkTaskResponse)
                .collect(Collectors.toList());
    }

    /** Per-user time-tracking summary (Module 3 — efficiency reports). */
    public List<java.util.Map<String, Object>> timeSummary(java.time.LocalDate from,
                                                            java.time.LocalDate to) {
        return workTaskRepo.timeSummary(from, to);
    }

    /** Full analytics snapshot for the Reports dashboard. */
    public java.util.Map<String, Object> analyticsSummary() {
        return workTaskRepo.analyticsSummary();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private WorkTask findAndAuthorize(String taskId, int userId) {
        WorkTask task = workTaskRepo.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        if (!Integer.valueOf(userId).equals(task.getAssignedTo())) {
            throw new BadRequestException("You are not authorized to act on this task.");
        }
        return task;
    }
}

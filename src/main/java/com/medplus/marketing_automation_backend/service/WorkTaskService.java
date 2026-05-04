package com.medplus.marketing_automation_backend.service;

import com.medplus.marketing_automation_backend.domain.WorkTask;
import com.medplus.marketing_automation_backend.dto.WorkTaskResponse;
import com.medplus.marketing_automation_backend.enums.CampaignStatus;
import com.medplus.marketing_automation_backend.enums.TaskStatus;
import com.medplus.marketing_automation_backend.exception.BadRequestException;
import com.medplus.marketing_automation_backend.exception.ResourceNotFoundException;
import com.medplus.marketing_automation_backend.repository.AssetInfoRepository;
import com.medplus.marketing_automation_backend.repository.WorkTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class WorkTaskService {

    private final WorkTaskRepository workTaskRepo;
    private final AssetInfoRepository assetInfoRepo;

    public WorkTaskService(WorkTaskRepository workTaskRepo,
                           AssetInfoRepository assetInfoRepo) {
        this.workTaskRepo  = workTaskRepo;
        this.assetInfoRepo = assetInfoRepo;
    }

    // -------------------------------------------------------------------------
    // Employee-facing
    // -------------------------------------------------------------------------

    /** Returns all tasks assigned to the given user. */
    public List<WorkTaskResponse> listMy(int userId) {
        return workTaskRepo.findByAssignedTo(userId).stream()
                .map(CampaignService::toWorkTaskResponse)
                .collect(Collectors.toList());
    }

    public WorkTaskResponse get(String taskId, int userId) {
        WorkTask task = findAndAuthorize(taskId, userId);
        return CampaignService.toWorkTaskResponse(task);
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
        // Persist uploaded asset URLs to the asset_info table
        if (assetUrls != null && !assetUrls.isEmpty()) {
            assetInfoRepo.insertAll(taskId, userId, assetUrls);
        }
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
        int updated = workTaskRepo.saveWorkerComment(taskId, userId, trim(comment));
        if (updated == 0) {
            throw new BadRequestException(
                    "Task cannot be held at its current status. Only ASSIGNED, IN_PROGRESS, or REWORK tasks can be held.");
        }
        return CampaignService.toWorkTaskResponse(
                workTaskRepo.findById(taskId).orElseThrow());
    }

    /**
     * Worker clears their comment and resumes the task (status → ASSIGNED).
     * Only works when the task was self-held by the worker (worker_comment IS NOT NULL).
     */
    @Transactional
    public WorkTaskResponse workerUnhold(String taskId, int userId) {
        findAndAuthorize(taskId, userId);
        int updated = workTaskRepo.clearWorkerComment(taskId, userId);
        if (updated == 0) {
            throw new BadRequestException(
                    "Task is not self-held or you are not the assigned worker.");
        }
        return CampaignService.toWorkTaskResponse(
                workTaskRepo.findById(taskId).orElseThrow());
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

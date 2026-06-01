package com.medplus.marketing_automation_backend.service;

import com.medplus.marketing_automation_backend.domain.WorkTask;
import com.medplus.marketing_automation_backend.dto.PagedResponse;
import com.medplus.marketing_automation_backend.dto.WorkTaskResponse;
import com.medplus.marketing_automation_backend.enums.CampaignStatus;
import com.medplus.marketing_automation_backend.enums.TaskStatus;
import com.medplus.marketing_automation_backend.event.TaskSubmittedForQcEvent;
import com.medplus.marketing_automation_backend.event.CommentAddedEvent;
import com.medplus.marketing_automation_backend.event.CommentRespondedEvent;
import com.medplus.marketing_automation_backend.event.ContentTaskSubmittedEvent;
import com.medplus.marketing_automation_backend.domain.Campaign;
import com.medplus.marketing_automation_backend.domain.User;
import com.medplus.marketing_automation_backend.exception.BadRequestException;
import com.medplus.marketing_automation_backend.exception.ResourceNotFoundException;
import com.medplus.marketing_automation_backend.domain.ApprovalLog;
import com.medplus.marketing_automation_backend.enums.ApprovalAction;
import com.medplus.marketing_automation_backend.domain.AssetInfo;
import com.medplus.marketing_automation_backend.repository.ApprovalLogRepository;
import com.medplus.marketing_automation_backend.repository.CampaignRepository;
import com.medplus.marketing_automation_backend.repository.UserRepository;
import com.medplus.marketing_automation_backend.repository.WorkTaskRepository;
import com.medplus.marketing_automation_backend.repository.WorkerCommentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medplus.marketing_automation_backend.dto.MyTasksListResponse;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;



@Slf4j
@Service
public class WorkTaskService {

    private final WorkTaskRepository      workTaskRepo;
    private final WorkerCommentRepository   workerCommentRepo;
    private final ApprovalLogRepository     approvalLogRepo;
    private final AutoCreatedTaskService    autoCreatedTaskService;
    private final UserRepository            userRepo;
    private final CollaborationService      collaborationService;
    private final CampaignRepository        campaignRepo;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationService       notificationService;

    public WorkTaskService(WorkTaskRepository workTaskRepo,
                           WorkerCommentRepository workerCommentRepo,
                           ApprovalLogRepository approvalLogRepo,
                           AutoCreatedTaskService autoCreatedTaskService,
                           UserRepository userRepo,
                           CollaborationService collaborationService,
                           CampaignRepository campaignRepo,
                           ApplicationEventPublisher eventPublisher,
                           NotificationService notificationService) {
        this.workTaskRepo           = workTaskRepo;
        this.workerCommentRepo      = workerCommentRepo;
        this.approvalLogRepo        = approvalLogRepo;
        this.autoCreatedTaskService  = autoCreatedTaskService;
        this.userRepo               = userRepo;
        this.collaborationService   = collaborationService;
        this.campaignRepo           = campaignRepo;
        this.eventPublisher         = eventPublisher;
        this.notificationService    = notificationService;
    }

    // -------------------------------------------------------------------------
    // Employee-facing
    // -------------------------------------------------------------------------

    private static final int MAX_IN_FLIGHT = 3;

    /**
     * Returns a paginated view of the current user's tasks with:
     * <ul>
     *   <li>Server-side search ({@code q}) and tab ({@code statusGroup}) filtering</li>
     *   <li>Tab badge counts (always from the full list, unaffected by search/tab)</li>
     *   <li>Per-task {@code canStart} flag computed from the real priority queue</li>
     *   <li>{@code inFlightFull} flag so the UI knows which lock message to display</li>
     * </ul>
     */
    public MyTasksListResponse listMyPaged(int userId, String search,
                                           String statusGroup, int page, int size) {
        // ── 1. Queue logic — always uses full unfiltered list ─────────────────
        int inFlight   = workTaskRepo.countInFlight(userId);
        boolean inFlightFull = inFlight >= MAX_IN_FLIGHT;
        int slots      = Math.max(0, MAX_IN_FLIGHT - inFlight);
        Set<String> startableIds = new HashSet<>(workTaskRepo.findStartableTaskIds(userId, slots));

        // ── 2. Tab badge counts (always full list, no search/tab filter) ──────
        Map<String, Long> tabCounts = workTaskRepo.getTabCounts(userId);

        // ── 3. Paged data ────────────────────────────────────────────────────
        long total      = workTaskRepo.countForMyTasks(userId, search, statusGroup);
        int  totalPages = size <= 0 ? 1 : (int) Math.ceil((double) total / size);
        List<WorkTask> rawPage = workTaskRepo.findForMyTasksPaged(userId, search, statusGroup, page, size);

        List<WorkTaskResponse> responses = rawPage.stream()
                .map(t -> {
                    WorkTaskResponse r = enrichWithComments(
                            CampaignService.toWorkTaskResponse(t), t.getTaskId());
                    r.setCanStart(startableIds.contains(t.getTaskId()));
                    return r;
                })
                .collect(Collectors.toList());
        responses = autoCreatedTaskService.enrichAll(responses);

        return new MyTasksListResponse(responses, total, totalPages, page, size, inFlightFull, tabCounts);
    }

    public WorkTaskResponse get(String taskId, int userId) {
        autoCreatedTaskService.assertRequestorCannotAccessAutoContentTask(taskId, userId);
        WorkTask task = findAndAuthorize(taskId, userId);
        return autoCreatedTaskService.enrich(
                enrichWithComments(CampaignService.toWorkTaskResponse(task), taskId));
    }

    public WorkTaskResponse requestContent(String taskId, int userId) {
        return autoCreatedTaskService.requestContent(taskId, userId);
    }

    public List<AssetInfo> getContentDeliverables(String taskId, int userId) {
        return autoCreatedTaskService.getContentDeliverables(taskId, userId);
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
        autoCreatedTaskService.assertRequestorCannotAccessAutoContentTask(taskId, userId);
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
        if (autoCreatedTaskService.isAutoGeneratedTask(taskId)) {
            autoCreatedTaskService.markInProgress(taskId);
        }
        // Auto-create collaboration the moment the worker starts the task so
        // the chat + assets are immediately available without a separate click.
        collaborationService.startCollaboration(taskId, userId);
        // Resolve TASK_ASSIGNED / MANAGER_REWORK / REQUESTOR_REWORK notifications — action taken
        notificationService.resolveNotifications(taskId,
                "TASK_ASSIGNED", "MANAGER_REWORK", "REQUESTOR_REWORK");
        return autoCreatedTaskService.enrich(CampaignService.toWorkTaskResponse(
                workTaskRepo.findById(taskId).orElseThrow()));
    }

    /**
     * Marks a task complete: sets status → MANAGER_QC_REVIEW, logs time spent, and
     * stores any submission notes / asset URL provided by the creator.
     * Task must be IN_PROGRESS.
     */
    @Transactional
    public WorkTaskResponse complete(String taskId, int userId,
                                     String submissionNotes, java.util.List<String> assetUrls) {
        // assetUrls is intentionally ignored — assets are now managed exclusively
        // via the collaboration panel (asset_info table, not work_tasks)
        autoCreatedTaskService.assertRequestorCannotAccessAutoContentTask(taskId, userId);
        WorkTask task = findAndAuthorize(taskId, userId);

        if (task.getStatus() != TaskStatus.IN_PROGRESS && task.getStatus() != TaskStatus.REWORK) {
            throw new BadRequestException("Task must be IN_PROGRESS or REWORK to complete. Current status: " + task.getStatus());
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
        LocalDateTime cycleStart = task.getAcceptedAt() != null ? task.getAcceptedAt() : task.getStartedAt();
        if (cycleStart != null) {
            minutes = (int) Duration.between(cycleStart, now).toMinutes();
        }
        // Add to any previously logged time (handles rework cycles).
        Integer previousLogged = task.getTotalTimeLoggedMinutes();
        int previous = previousLogged == null ? 0 : previousLogged;
        int totalMinutes = previous + Math.max(0, minutes);

        int updated;
        if (autoCreatedTaskService.isAutoGeneratedTask(taskId)) {
            updated = workTaskRepo.completeAutoAssigned(taskId, userId, now, totalMinutes, trim(submissionNotes));
            if (updated == 0) {
                throw new BadRequestException("Unable to complete task — please refresh and try again.");
            }
            autoCreatedTaskService.markCompleted(taskId);
            userRepo.decrementActiveTasks((long) userId);
            workTaskRepo.deactivateCollaboration(taskId);
            // Notify the designer who requested this content task
            autoCreatedTaskService.findRequesterByContentTaskId(taskId).ifPresent(designerId -> {
                String writerName = userRepo.findById((long) userId)
                        .map(u -> u.getFullName() != null ? u.getFullName() : "Content Writer")
                        .orElse("Content Writer");
                eventPublisher.publishEvent(new ContentTaskSubmittedEvent(taskId, writerName, designerId));
            });
        } else {
            updated = workTaskRepo.complete(taskId, userId, now, totalMinutes, trim(submissionNotes));
            if (updated == 0) {
                throw new BadRequestException("Unable to complete task — please refresh and try again.");
            }
            // Rule 5: task is now MANAGER_QC_REVIEW — deactivate collaboration
            workTaskRepo.deactivateCollaboration(taskId);
            // QC submission resolves any open worker comments.
            workerCommentRepo.markAllAnswered(taskId);
            // Close any linked auto-generated content task — the designer has
            // finished and no further content support is needed.
            autoCreatedTaskService.closeLinkedContentTaskOnSourceQcSubmit(taskId);
            // Notify managers that a task was submitted for QC
            String workerName = userRepo.findById((long) userId)
                    .map(u -> u.getFullName() != null ? u.getFullName() : "Someone")
                    .orElse("Someone");
            eventPublisher.publishEvent(new TaskSubmittedForQcEvent(taskId, userId, workerName));
        }
        return autoCreatedTaskService.enrich(CampaignService.toWorkTaskResponse(
                workTaskRepo.findById(taskId).orElseThrow()));
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
        WorkTask task = workTaskRepo.findById(taskId).orElseThrow(() ->
                new BadRequestException("Task not found: " + taskId));
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
        // Notify the campaign requestor that a comment was added
        if (task.getCampaignId() != null) {
            Campaign campaign = campaignRepo.findById(task.getCampaignId()).orElse(null);
            if (campaign != null && campaign.getRequestorId() != null) {
                User worker = userRepo.findById((long) userId).orElse(null);
                String workerName = worker != null && worker.getFullName() != null ? worker.getFullName() : "Worker";
                eventPublisher.publishEvent(new CommentAddedEvent(taskId, workerName, campaign.getRequestorId()));
            }
        }
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
        // Worker resumed — resolve COMMENT_RESPONDED notification (they saw it and acted)
        notificationService.resolveNotifications(taskId, "COMMENT_RESPONDED");
        return CampaignService.toWorkTaskResponse(
                workTaskRepo.findById(taskId).orElseThrow());
    }

    /** Mark a single worker comment as answered. Any participant can do this. */
    @Transactional
    public void markCommentAnswered(String taskId, int commentId, int actorUserId) {
        workerCommentRepo.markAnswered(commentId);
        // Requestor answered comment — resolve COMMENT_ADDED notification for that task
        notificationService.resolveNotifications(taskId, "COMMENT_ADDED");
        // Notify the task assignee that their comment was addressed
        WorkTask task = workTaskRepo.findById(taskId).orElse(null);
        if (task != null && task.getAssignedTo() != null && task.getAssignedTo() != actorUserId) {
            User actor = userRepo.findById((long) actorUserId).orElse(null);
            String responderName = actor != null && actor.getFullName() != null ? actor.getFullName() : "Requestor";
            eventPublisher.publishEvent(new CommentRespondedEvent(taskId, responderName, task.getAssignedTo()));
        }
        // If no unanswered comments remain and task is still HELD, auto-unhold back to pre-hold status
        if (task != null && task.getStatus() == TaskStatus.HELD) {
            boolean noMoreOpen = workerCommentRepo.findActiveByTaskId(taskId).isEmpty();
            if (noMoreOpen) {
                int updated = workTaskRepo.clearHoldByTaskId(taskId);
                if (updated > 0) {
                    log.info("COMMENT answered | auto-cleared hold on task={} by actor={}", taskId, actorUserId);
                    approvalLogRepo.insert(ApprovalLog.builder()
                            .taskId(taskId).reviewerId(actorUserId)
                            .actionTaken(ApprovalAction.UNHOLD).build());
                    WorkTask restored = workTaskRepo.findById(taskId).orElse(null);
                    if (restored != null && restored.getStatus() == TaskStatus.IN_PROGRESS) {
                        workTaskRepo.activateCollaboration(taskId);
                    }
                    notificationService.resolveNotifications(taskId, "TASK_HELD_BY_MANAGER");
                }
            }
        }
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
            Boolean autoGeneratedOnly,
            LocalDate dateFrom, LocalDate dateTo,
            String actionDoneBy,
            String storeId,
            String sourceTaskId,
            String contentRequestedBy,
            String contentRequestStatus,
            int page, int size) {

        PagedResponse<WorkTask> raw = workTaskRepo.findAllPaged(
                taskId, campaignId, requestorName, assigneeName,
                taskType, priority, status, autoGeneratedOnly, dateFrom, dateTo,
                actionDoneBy, storeId, sourceTaskId, contentRequestedBy, contentRequestStatus, page, size);

        List<WorkTaskResponse> mapped = raw.content().stream()
                .map(CampaignService::toWorkTaskResponse)
                .collect(Collectors.toList());
        mapped = autoCreatedTaskService.enrichAll(mapped);
        return PagedResponse.of(mapped, raw.totalElements(), raw.page(), raw.size());
    }

    public List<WorkTaskResponse> listPendingQcReview() {
        return workTaskRepo.findPendingQcReview().stream()
                .map(CampaignService::toWorkTaskResponse)
                .collect(Collectors.toList());
    }

    public PagedResponse<WorkTaskResponse> listPendingQcReviewPaged(
            int excludeUserId,
            java.util.List<String> allowedWorkerRoleIds,
            String search, LocalDate dateFrom, LocalDate dateTo,
            int page, int size) {
        PagedResponse<WorkTask> raw = workTaskRepo.findPendingQcReviewPaged(
                excludeUserId, allowedWorkerRoleIds, search, dateFrom, dateTo, page, size);
        List<WorkTaskResponse> mapped = raw.content().stream()
                .map(CampaignService::toWorkTaskResponse)
                .collect(Collectors.toList());
        mapped = autoCreatedTaskService.enrichAll(mapped);
        return PagedResponse.of(mapped, raw.totalElements(), raw.page(), raw.size());
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
    // Per-task reference files (uploaded by the campaign requestor)
    // -------------------------------------------------------------------------

    /**
     * Saves one or more reference files against a specific work task.
     * The caller must be the campaign requestor (verified before calling this method).
     */
    public void addTaskFiles(String taskId, int campaignId,
                              List<String> fileUrls, List<String> fileOriginalNames) {
        for (int i = 0; i < fileUrls.size(); i++) {
            String url  = fileUrls.get(i);
            String name = (fileOriginalNames != null && i < fileOriginalNames.size())
                    ? fileOriginalNames.get(i) : null;
            campaignRepo.insertTaskFile(campaignId, taskId, url, name);
        }
    }

    /**
     * Removes a single reference file from a specific work task.
     * The caller must be the campaign requestor (verified before calling this method).
     */
    public void removeTaskFile(String taskId, String fileUrl) {
        campaignRepo.deleteTaskFile(taskId, fileUrl);
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

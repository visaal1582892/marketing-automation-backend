package com.medplus.marketing_automation_backend.service;

import com.medplus.marketing_automation_backend.domain.ApprovalLog;
import com.medplus.marketing_automation_backend.domain.Campaign;
import com.medplus.marketing_automation_backend.domain.User;
import com.medplus.marketing_automation_backend.domain.WorkTask;
import com.medplus.marketing_automation_backend.dto.ApprovalRequest;
import com.medplus.marketing_automation_backend.dto.WorkTaskResponse;
import com.medplus.marketing_automation_backend.enums.ApprovalAction;
import com.medplus.marketing_automation_backend.enums.CampaignStatus;
import com.medplus.marketing_automation_backend.enums.TaskStatus;
import com.medplus.marketing_automation_backend.event.ManagerQcApprovedEvent;
import com.medplus.marketing_automation_backend.event.RequestorQcApprovedEvent;
import com.medplus.marketing_automation_backend.event.ManagerReworkEvent;
import com.medplus.marketing_automation_backend.event.ManagerRejectEvent;
import com.medplus.marketing_automation_backend.event.RequestorReworkEvent;
import com.medplus.marketing_automation_backend.exception.BadRequestException;
import com.medplus.marketing_automation_backend.exception.ResourceNotFoundException;
import com.medplus.marketing_automation_backend.repository.ApprovalLogRepository;
import com.medplus.marketing_automation_backend.repository.CampaignRepository;
import com.medplus.marketing_automation_backend.repository.UserRepository;
import com.medplus.marketing_automation_backend.repository.WorkTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ApprovalService {

    private final WorkTaskRepository     workTaskRepo;
    private final ApprovalLogRepository  approvalLogRepo;
    private final CampaignRepository     campaignRepo;
    private final UserRepository         userRepo;
    private final ApplicationEventPublisher eventPublisher;

    public ApprovalService(WorkTaskRepository workTaskRepo,
                           ApprovalLogRepository approvalLogRepo,
                           CampaignRepository campaignRepo,
                           UserRepository userRepo,
                           ApplicationEventPublisher eventPublisher) {
        this.workTaskRepo    = workTaskRepo;
        this.approvalLogRepo = approvalLogRepo;
        this.campaignRepo    = campaignRepo;
        this.userRepo        = userRepo;
        this.eventPublisher  = eventPublisher;
    }

    // -------------------------------------------------------------------------
    // Manager reviews a task from QC
    // -------------------------------------------------------------------------

    @Transactional
    public WorkTaskResponse review(String taskId, ApprovalRequest req, User reviewer) {
        log.info("QC review | taskId={} action={} reviewerId={} reviewerEmail={}",
                taskId, req.getAction(), reviewer.getUserId(), reviewer.getEmail());
        WorkTask task = workTaskRepo.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        if (task.getStatus() != TaskStatus.MANAGER_QC_REVIEW) {
            throw new BadRequestException("Task must be in MANAGER_QC_REVIEW to review. Current: " + task.getStatus());
        }
        // Self-approval guard — a user who also holds a worker role cannot approve their own task.
        if (reviewer.getUserId() != null && task.getAssignedTo() != null
                && reviewer.getUserId().intValue() == task.getAssignedTo()) {
            throw new BadRequestException("You cannot approve a task that was assigned to you.");
        }
        // Defensive: if the parent campaign is already terminal, refuse the QC
        // action — this would otherwise re-flip the campaign back to COMPLETED
        // from REJECTED (or vice-versa) on a stale tab.
        CampaignStatus campaignStatus = campaignRepo.findById(task.getCampaignId())
                .map(c -> c.getStatus())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Parent campaign not found: " + task.getCampaignId()));
        if (campaignStatus == CampaignStatus.COMPLETED || campaignStatus == CampaignStatus.REJECTED) {
            throw new BadRequestException(
                    "Cannot QC-review a task whose campaign is already " + campaignStatus);
        }

        ApprovalLog entry = ApprovalLog.builder()
                .taskId(task.getTaskId())
                .reviewerId(reviewer.getUserId().intValue())
                .actionTaken(req.getAction())
                .comments(req.getComments())
                .build();
        approvalLogRepo.insert(entry);

        switch (req.getAction()) {
            case REQUESTOR_REWORK -> throw new BadRequestException(
                    "Use the requestor-rework endpoint to submit a requestor rework request.");
            case APPROVED -> {
                // Manager approves → REQUESTOR_QC_REVIEW (requestor must still sign off)
                workTaskRepo.markManagerApproved(taskId);
                workTaskRepo.deactivateCollaboration(taskId);
                if (task.getAssignedTo() != null) {
                    userRepo.decrementActiveTasks(task.getAssignedTo().longValue());
                }
                // Check if all tasks are now in a terminal/requestor-qc state
                int stillActive = workTaskRepo.countIncomplete(task.getCampaignId());
                if (stillActive == 0) {
                    campaignRepo.updateStatus(task.getCampaignId(), CampaignStatus.REQUESTOR_QC_REVIEW);
                    log.info("MANAGER QC APPROVED — campaign moved to REQUESTOR_QC_REVIEW | taskId={} campaignId={}",
                            taskId, task.getCampaignId());
                } else {
                    log.info("MANAGER QC APPROVED | taskId={} campaignId={} remainingTasks={}",
                            taskId, task.getCampaignId(), stillActive);
                }
                // Notify worker and requestor
                int workerId    = task.getAssignedTo() != null ? task.getAssignedTo() : -1;
                int requestorId = task.getRequestorId() != null ? task.getRequestorId() : -1;
                String managerName = reviewer.getFullName() != null ? reviewer.getFullName() : "Manager";
                if (workerId > 0) {
                    eventPublisher.publishEvent(new ManagerQcApprovedEvent(taskId, managerName, workerId, requestorId));
                }
            }
            case NEEDS_REWORK -> {
                workTaskRepo.markRework(taskId);
                // Do NOT auto-sync the linked content task here.
                // The designer decides whether to send the content task for rework
                // by explicitly clicking "Content Rework". Doing it automatically
                // caused the content task to flip to REWORK on every QC rejection
                // without the designer's intent.
                workTaskRepo.activateCollaboration(taskId); // REWORK = task back to worker → re-activate
                log.info("QC NEEDS_REWORK | taskId={} campaignId={} comment={}",
                        taskId, task.getCampaignId(), req.getComments());
                if (task.getAssignedTo() != null) {
                    String managerName = reviewer.getFullName() != null ? reviewer.getFullName() : "Manager";
                    eventPublisher.publishEvent(new ManagerReworkEvent(taskId, managerName, task.getAssignedTo()));
                }
            }
            case REJECTED -> {
                workTaskRepo.markRejected(taskId);
                if (task.getAssignedTo() != null) {
                    userRepo.decrementActiveTasks(task.getAssignedTo().longValue());
                }
                cancelSiblingTasksAndRefreshCounters(task.getCampaignId(), task.getTaskId());
                // Sibling cancellations share the same second; bump the rejected row last.
                workTaskRepo.touchUpdatedAt(taskId);
                campaignRepo.updateStatusAndNotes(task.getCampaignId(), CampaignStatus.REJECTED,
                        "Rejected by reviewer: " + (req.getComments() != null ? req.getComments() : ""));
                log.info("QC REJECTED — campaign rejected | taskId={} campaignId={} comment={}",
                        taskId, task.getCampaignId(), req.getComments());
                if (task.getAssignedTo() != null) {
                    String managerName = reviewer.getFullName() != null ? reviewer.getFullName() : "Manager";
                    eventPublisher.publishEvent(new ManagerRejectEvent(taskId, managerName, task.getAssignedTo()));
                }
            }
        }

        return CampaignService.toWorkTaskResponse(
                workTaskRepo.findById(taskId).orElseThrow());
    }

    /**
     * Cancels every still-open sibling task on the campaign (excluding the
     * task that triggered the rejection — that one is handled by the caller)
     * and decrements each affected assignee's active-task counter.
     */
    private void cancelSiblingTasksAndRefreshCounters(int campaignId, String excludingTaskId) {
        List<java.util.Map<String, Object>> cancelled =
                workTaskRepo.cancelOpenTasksForCampaign(campaignId);
        for (java.util.Map<String, Object> row : cancelled) {
            Object tid = row.get("task_id");
            Object uid = row.get("assigned_to");
            if (tid != null && tid.toString().equals(excludingTaskId)) continue;
            if (uid != null) {
                userRepo.decrementActiveTasks(((Number) uid).longValue());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Requestor approves a REQUESTOR_QC_REVIEW task
    // -------------------------------------------------------------------------

    /**
     * Allows the campaign requestor (or Admin) to approve a task that is in
     * REQUESTOR_QC_REVIEW state. The task moves to COMPLETED and requestor_approved_at
     * is stamped. If all tasks are now done, the campaign is marked COMPLETED.
     */
    @Transactional
    public WorkTaskResponse requestorApproveTask(int campaignId, String taskId, String comment, User requestor) {
        log.info("REQUESTOR approve | campaignId={} taskId={} requestorId={}", campaignId, taskId, requestor.getUserId());
        Campaign campaign = campaignRepo.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found: " + campaignId));

        boolean isAdmin = requestor.hasRole("Admin");
        if (!isAdmin && !campaign.getRequestorId().equals(requestor.getUserId().intValue())) {
            throw new BadRequestException("Only the campaign owner can approve a delivered task.");
        }

        WorkTask task = workTaskRepo.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        if (task.getCampaignId() == null || task.getCampaignId().intValue() != campaignId) {
            throw new BadRequestException("Task " + taskId + " does not belong to campaign " + campaignId);
        }

        if (task.getStatus() != TaskStatus.REQUESTOR_QC_REVIEW) {
            throw new BadRequestException(
                    "Only REQUESTOR_QC_REVIEW tasks can be approved by requestor. Current status: " + task.getStatus());
        }

        ApprovalLog entry = ApprovalLog.builder()
                .taskId(taskId)
                .reviewerId(requestor.getUserId().intValue())
                .actionTaken(ApprovalAction.APPROVED)
                .comments(comment)
                .build();
        approvalLogRepo.insert(entry);

        workTaskRepo.markRequestorApproved(taskId);

        int remainingPending = workTaskRepo.countPendingRequestorApproval(task.getCampaignId());
        if (remainingPending == 0) {
            campaignRepo.updateStatus(task.getCampaignId(), CampaignStatus.COMPLETED);
            log.info("REQUESTOR approved last task — campaign completed | taskId={} campaignId={}",
                    taskId, task.getCampaignId());
        } else {
            log.info("REQUESTOR approved | taskId={} campaignId={} remainingPending={}",
                    taskId, task.getCampaignId(), remainingPending);
        }

        // Notify the task worker
        if (task.getAssignedTo() != null && task.getAssignedTo() > 0) {
            String requestorName = requestor.getFullName() != null ? requestor.getFullName() : "Requestor";
            eventPublisher.publishEvent(new RequestorQcApprovedEvent(taskId, requestorName, task.getAssignedTo()));
        }

        return CampaignService.toWorkTaskResponse(
                workTaskRepo.findById(taskId).orElseThrow());
    }

    // -------------------------------------------------------------------------
    // Audit history
    // -------------------------------------------------------------------------

    public List<ApprovalLog> getHistory(String taskId) {
        return approvalLogRepo.findByTaskId(taskId);
    }

    // -------------------------------------------------------------------------
    // Rework quality report
    // -------------------------------------------------------------------------

    /**
     * Aggregated rework stats used by the manager's quality report.
     * Returns two lists:
     *   byWorker – one row per assignee showing how many reworks their tasks received.
     *   byTask   – one row per task that was reworked ≥ once, with the latest comment.
     */
    public Map<String, Object> getReworkReport() {
        return Map.of(
                "byWorker", approvalLogRepo.reworkStatsByWorker(),
                "byTask",   approvalLogRepo.reworkStatsByTask()
        );
    }

    // -------------------------------------------------------------------------
    // Requestor requests rework on a COMPLETED task
    // -------------------------------------------------------------------------

    /**
     * Allows the campaign requestor (or admin) to send a COMPLETED task back
     * for another rework cycle, providing a message explaining what needs changing.
     *
     * <p>The task is flipped from COMPLETED → REWORK so the assignee picks it up
     * again. If the parent campaign was already marked COMPLETED (all tasks done),
     * it is re-opened to IN_PROGRESS so it correctly re-appears as active.
     */
    @Transactional
    public WorkTaskResponse requestorReworkTask(int campaignId, String taskId,
                                                String message, User requestor) {
        log.info("REQUESTOR rework | campaignId={} taskId={} requestorId={} requestorEmail={}",
                campaignId, taskId, requestor.getUserId(), requestor.getEmail());
        Campaign campaign = campaignRepo.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found: " + campaignId));

        boolean isAdmin = requestor.hasRole("Admin");
        if (!isAdmin && !campaign.getRequestorId().equals(requestor.getUserId().intValue())) {
            throw new BadRequestException("Only the campaign owner can request rework of a delivered task.");
        }

        WorkTask task = workTaskRepo.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        if (task.getCampaignId() == null || task.getCampaignId().intValue() != campaignId) {
            throw new BadRequestException("Task " + taskId + " does not belong to campaign " + campaignId);
        }

        if (task.getStatus() != TaskStatus.REQUESTOR_QC_REVIEW && task.getStatus() != TaskStatus.COMPLETED) {
            throw new BadRequestException(
                    "Only tasks in REQUESTOR_QC_REVIEW or COMPLETED status can be sent for requestor rework. Current status: " + task.getStatus());
        }

        ApprovalLog entry = ApprovalLog.builder()
                .taskId(taskId)
                .reviewerId(requestor.getUserId().intValue())
                .actionTaken(ApprovalAction.REQUESTOR_REWORK)
                .comments(message)
                .build();
        approvalLogRepo.insert(entry);

        workTaskRepo.markRework(taskId);
        workTaskRepo.activateCollaboration(taskId);

        // Re-increment the assignee's active-task counter (was decremented when manager approved).
        if (task.getAssignedTo() != null) {
            userRepo.incrementActiveTasks(task.getAssignedTo().longValue());
        }
        if (task.getAssignedTo() != null) {
            String requestorName = requestor.getFullName() != null ? requestor.getFullName() : "Requestor";
            eventPublisher.publishEvent(new RequestorReworkEvent(taskId, requestorName, task.getAssignedTo()));
        }

        // Re-open the campaign if it was in REQUESTOR_QC_REVIEW or already COMPLETED.
        if (campaign.getStatus() == CampaignStatus.REQUESTOR_QC_REVIEW
                || campaign.getStatus() == CampaignStatus.COMPLETED) {
            campaignRepo.updateStatus(campaignId, CampaignStatus.IN_PROGRESS);
            log.info("REQUESTOR rework — campaign re-opened | campaignId={}", campaignId);
        }

        log.info("REQUESTOR rework submitted | campaignId={} taskId={} message={}",
                campaignId, taskId, message);

        return CampaignService.toWorkTaskResponse(
                workTaskRepo.findById(taskId).orElseThrow());
    }
}

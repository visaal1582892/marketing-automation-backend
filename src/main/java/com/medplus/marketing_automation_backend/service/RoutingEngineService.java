package com.medplus.marketing_automation_backend.service;

import com.medplus.marketing_automation_backend.domain.Campaign;
import com.medplus.marketing_automation_backend.domain.CampaignDeliverable;
import com.medplus.marketing_automation_backend.domain.RoleTask;
import com.medplus.marketing_automation_backend.domain.User;
import com.medplus.marketing_automation_backend.domain.WorkTask;
import com.medplus.marketing_automation_backend.dto.CapacityReport;
import com.medplus.marketing_automation_backend.dto.WorkTaskAnswerRequest;
import com.medplus.marketing_automation_backend.dto.WorkTaskResponse;
import com.medplus.marketing_automation_backend.enums.CampaignStatus;
import com.medplus.marketing_automation_backend.enums.TaskStatus;
import com.medplus.marketing_automation_backend.exception.InsufficientCapacityException;
import com.medplus.marketing_automation_backend.repository.CampaignRepository;
import com.medplus.marketing_automation_backend.repository.CollaboratorRepository;
import com.medplus.marketing_automation_backend.repository.RoutingConfigRepository;
import com.medplus.marketing_automation_backend.repository.UserRepository;
import com.medplus.marketing_automation_backend.repository.WorkTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Module 2 — Backend Logic & Workload Engine.
 *
 * <p>Performs auto-tagging, role lookup, and workload-aware round-robin
 * assignment. Routing is all-or-nothing per campaign: if any deliverable
 * cannot be assigned, {@link InsufficientCapacityException} is raised and
 * the wrapping transaction rolls back, leaving no partially-routed campaign.
 * Capacity issues are resolved by the manager holding low-priority tasks via
 * the "Held Tasks" workflow before retrying.
 */
@Slf4j
@Service
public class RoutingEngineService {

    private final CampaignRepository      campaignRepo;
    private final RoutingConfigRepository routingConfigRepo;
    private final UserRepository          userRepo;
    private final WorkTaskRepository      workTaskRepo;
    private final CollaboratorRepository  collaboratorRepo;
    private final QuestionnaireService    questionnaireService;

    public RoutingEngineService(CampaignRepository campaignRepo,
                                RoutingConfigRepository routingConfigRepo,
                                UserRepository userRepo,
                                WorkTaskRepository workTaskRepo,
                                CollaboratorRepository collaboratorRepo,
                                QuestionnaireService questionnaireService) {
        this.campaignRepo          = campaignRepo;
        this.routingConfigRepo     = routingConfigRepo;
        this.userRepo              = userRepo;
        this.workTaskRepo          = workTaskRepo;
        this.collaboratorRepo      = collaboratorRepo;
        this.questionnaireService  = questionnaireService;
    }

    // -------------------------------------------------------------------------
    // Public entry — called on campaign creation to auto-route deliverables.
    // -------------------------------------------------------------------------

    /**
     * Creates one work_task per un-routed deliverable for the campaign.
     * All-or-nothing: throws {@link InsufficientCapacityException} on the first
     * unroutable deliverable so the whole transaction rolls back cleanly.
     */
    @Transactional
    public void route(int campaignId) {
        route(campaignId, Collections.emptyMap());
    }

    /**
     * @param questionnaireByGranularTaskId answers from the request form, keyed by {@code granular_task_id}
     */
    @Transactional
    public void route(int campaignId,
                      Map<String, List<WorkTaskAnswerRequest.AnswerItem>> questionnaireByGranularTaskId) {
        log.info("ROUTING start | campaignId={}", campaignId);

        Campaign campaign = campaignRepo.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + campaignId));

        List<RoutingTarget> targets = collectRoutingTargets(campaign);

        if (targets.isEmpty()) {
            log.info("ROUTING skip — no unrouted deliverables | campaignId={}", campaignId);
            campaignRepo.updateStatus(campaignId, CampaignStatus.IN_PROGRESS);
            return;
        }

        log.debug("ROUTING targets | campaignId={} count={} targets={}", campaignId, targets.size(), targets);

        Map<String, List<WorkTaskAnswerRequest.AnswerItem>> qa = questionnaireByGranularTaskId == null
                ? Collections.emptyMap()
                : questionnaireByGranularTaskId;

        // Prevent self-assignment: exclude the requestor from the routing pool.
        int requestorId = campaign.getRequestorId() == null ? -1 : campaign.getRequestorId();

        for (RoutingTarget t : targets) {
            if (t.roleId == null) {
                log.error("ROUTING failed — no role mapping | campaignId={} granularTaskId={}",
                        campaignId, t.granularTaskId);
                throw new InsufficientCapacityException(
                        "No role mapping configured for deliverable "
                                + (t.granularTaskId == null ? "(no task id)" : t.granularTaskId),
                        capacityReport(campaignId));
            }
            boolean ok = assignTask(campaignId, requestorId, t.granularTaskId, t.roleId, qa);
            if (!ok) {
                log.error("ROUTING failed — no active users | campaignId={} roleId={} granularTaskId={}",
                        campaignId, t.roleId, t.granularTaskId);
                throw new InsufficientCapacityException(
                        "No active users found in role '" + t.roleId + "'. "
                                + "Please add team members to this role before submitting.",
                        capacityReport(campaignId));
            }
        }

        log.info("ROUTING complete | campaignId={} tasksRouted={}", campaignId, targets.size());
        campaignRepo.updateStatus(campaignId, CampaignStatus.IN_PROGRESS);
    }

    /**
     * Collects the deliverables that still need a work_task. HELD and
     * CANCELLED tasks are treated as "not routed" — their slot is gone, so
     * the campaign needs a fresh assignment to fill it. Falls back to the
     * legacy role/task mapping when a campaign has no deliverables (older
     * test data).
     */
    private List<RoutingTarget> collectRoutingTargets(Campaign campaign) {
        int campaignId = campaign.getCampaignId();
        String defaultRoleId = null; // requirement_type_id removed; role resolved per granular task

        List<CampaignDeliverable> specs    = campaignRepo.findDeliverablesByCampaignId(campaignId);
        List<WorkTask>            existing = workTaskRepo.findByCampaignId(campaignId);

        java.util.Set<String> liveRoutedKeys = existing.stream()
                .filter(t -> t.getStatus() != TaskStatus.CANCELLED && t.getStatus() != TaskStatus.HELD)
                .map(WorkTask::getGranularTaskId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        List<RoutingTarget> targets = new ArrayList<>();
        if (!specs.isEmpty()) {
            for (CampaignDeliverable spec : specs) {
                if (spec.getGranularTaskId() != null
                        && liveRoutedKeys.contains(spec.getGranularTaskId())) {
                    continue;
                }
                String roleId = resolveRole(spec.getGranularTaskId(), defaultRoleId);
                targets.add(new RoutingTarget(spec.getGranularTaskId(), roleId));
            }
        } else if (existing.isEmpty() && defaultRoleId != null) {
            List<RoleTask> roleTasks = routingConfigRepo.findRoleTaskMappingsByRole(defaultRoleId);
            if (roleTasks.isEmpty()) {
                    targets.add(new RoutingTarget(null, defaultRoleId));
                } else {
                    for (RoleTask t : roleTasks) {
                        targets.add(new RoutingTarget(t.getTaskId(), defaultRoleId));
                }
            }
        }
        return targets;
    }

    /**
     * Read-only capacity snapshot for the marketing-head approval gate.
     *
     * <p>For every un-routed deliverable on the campaign, we resolve its
     * target role and aggregate per-role demand against the live workload of
     * each active user in that role. The marketing head uses {@code blocked}
     * + {@code users[].openTasks} to decide which low-priority tasks to
     * hold before retrying the approval.
     */
    public CapacityReport capacityReport(int campaignId) {
        Campaign campaign = campaignRepo.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + campaignId));
        List<RoutingTarget> targets = collectRoutingTargets(campaign);

        Map<String, Integer> requiredByRole = new LinkedHashMap<>();
        for (RoutingTarget t : targets) {
            String key = t.roleId == null ? "" : t.roleId;
            requiredByRole.merge(key, 1, Integer::sum);
        }

        List<CapacityReport.RoleCapacity> roles = new ArrayList<>();
        boolean canRoute = true;
        for (Map.Entry<String, Integer> e : requiredByRole.entrySet()) {
            String roleId = e.getKey().isEmpty() ? null : e.getKey();
            int required = e.getValue();

            List<User> activeUsers = roleId == null
                    ? java.util.Collections.emptyList()
                    : userRepo.findByRole(roleId);

            String roleName = activeUsers.isEmpty() ? null : activeUsers.get(0).getPrimaryRoleName();

            List<CapacityReport.UserCapacity> userCaps = new ArrayList<>();
            for (User u : activeUsers) {
                int liveActive = workTaskRepo.countActiveTasksByUser(u.getUserId().intValue());

                List<WorkTaskResponse> openTasks = workTaskRepo
                        .findHoldableByUser(u.getUserId().intValue()).stream()
                        .map(CampaignService::toWorkTaskResponse)
                        .collect(Collectors.toList());

                userCaps.add(CapacityReport.UserCapacity.builder()
                        .userId(u.getUserId().intValue())
                        .fullName(u.getFullName())
                        .roleId(u.getPrimaryRoleId())
                        .roleName(u.getPrimaryRoleName())
                        .currentActiveTasks(liveActive)
                        .openTasks(openTasks)
                        .build());
            }

            // Blocked only when there are no users at all in the required role
            boolean blocked = roleId == null || activeUsers.isEmpty();
            if (blocked) canRoute = false;

            roles.add(CapacityReport.RoleCapacity.builder()
                    .roleId(roleId)
                    .roleName(roleName == null ? roleId : roleName)
                    .requiredSlots(required)
                    .availableSlots(activeUsers.size())
                    .blocked(blocked)
                    .users(userCaps)
                    .build());
        }

        return CapacityReport.builder()
                .canRoute(canRoute)
                .unroutedDeliverables(targets.size())
                .roles(roles)
                .build();
    }

    /**
     * Unholds a single previously-held task and re-routes it through the
     * standard auto-routing pick — the new assignee may differ from the one
     * who was holding it. Throws {@link InsufficientCapacityException} if no
     * user in the relevant role has a free slot; the task stays HELD until
     * the manager frees a slot and retries.
     */
    @Transactional
    public void unholdAndReassign(String taskId) {
        log.info("ROUTING unholdReassign | taskId={}", taskId);
        WorkTask task = workTaskRepo.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        if (task.getStatus() != TaskStatus.HELD) {
            throw new IllegalStateException("Task is not on hold (current status: " + task.getStatus() + ")");
        }

        Campaign campaign = campaignRepo.findById(task.getCampaignId())
                .orElseThrow(() -> new IllegalArgumentException("Parent campaign missing"));
        if (campaign.getStatus() == CampaignStatus.REJECTED
                || campaign.getStatus() == CampaignStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Parent campaign is already " + campaign.getStatus() + " — cannot reassign held task.");
        }

        String defaultRoleId = null; // requirement_type_id removed; role resolved per granular task
        String roleId = resolveRole(task.getGranularTaskId(), defaultRoleId);
        if (roleId == null) {
            throw new InsufficientCapacityException(
                    "No role mapping configured for this task — cannot reassign.",
                    capacityReport(task.getCampaignId()));
        }

        int requestorId = campaign.getRequestorId() == null ? -1 : campaign.getRequestorId();

        Optional<User> bestUser = userRepo.findBestAvailableUserInRole(roleId, requestorId);
        if (bestUser.isEmpty()) {
            throw new IllegalStateException(
                    "No active users found in role '" + roleId + "'. "
                            + "Please add users to this role before retrying.");
        }

        User assignee = bestUser.get();
        int updated = workTaskRepo.reassignFromHeld(task.getTaskId(), assignee.getUserId().intValue());
        if (updated == 0) {
            throw new IllegalStateException("Failed to unhold task — refresh and retry.");
        }
        userRepo.incrementActiveTasks(assignee.getUserId());
        log.info("ROUTING unhold complete | taskId={} newAssigneeId={} newAssigneeName={}",
                task.getTaskId(), assignee.getUserId(), assignee.getFullName());
    }

    /**
     * Returns all active users who are eligible to handle a specific held task,
     * along with their current live workload. Used by the manual-assign flow on
     * the Held Tasks page.
     */
    public List<User> getEligibleUsersForTask(WorkTask task) {
        if ("TASK-OTHER".equals(task.getGranularTaskId())) {
            return userRepo.findAllMarketingWorkers();
        }
        Campaign campaign = campaignRepo.findById(task.getCampaignId())
                .orElseThrow(() -> new IllegalArgumentException("Parent campaign missing"));
        String defaultRoleId = null; // requirement_type_id removed; role resolved per granular task
        String roleId = resolveRole(task.getGranularTaskId(), defaultRoleId);
        if (roleId == null) return java.util.Collections.emptyList();
        return userRepo.findByRole(roleId);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Picks the right role for a granular task, preferring the requirement's
     * default role if it actually services that task.
     */
    private String resolveRole(String granularTaskId, String defaultRoleId) {
        if (granularTaskId == null) return defaultRoleId;
        List<String> roles = routingConfigRepo.findActiveRoleIdsForTask(granularTaskId);
        if (roles.isEmpty()) return defaultRoleId;
        if (defaultRoleId != null && roles.contains(defaultRoleId)) {
            return defaultRoleId;
        }
        return roles.get(0);
    }

    /**
     * @param requestorId user to exclude from the routing pool (self-assignment prevention);
     *                    pass {@code -1} to exclude nobody
     */
    private boolean assignTask(int campaignId, int requestorId, String granularTaskId, String roleId,
                                Map<String, List<WorkTaskAnswerRequest.AnswerItem>> questionnaireByGranularTaskId) {

        // "Other" tasks always start HELD so the Marketing Manager can
        // manually choose the right assignee — do NOT assign to anyone yet.
        boolean isOtherTask = "TASK-OTHER".equals(granularTaskId);

        if (isOtherTask) {
            WorkTask workTask = WorkTask.builder()
                    .campaignId(campaignId)
                    .granularTaskId(granularTaskId)
                    .status(TaskStatus.HELD)
                    // assignedTo intentionally omitted — stays NULL until manager routes it
                    .build();
            String workTaskId = workTaskRepo.insert(workTask);
            log.info("ROUTING assignTask (HELD/unassigned) | campaignId={} granularTaskId={}",
                    campaignId, granularTaskId);
            if (granularTaskId != null) {
                List<WorkTaskAnswerRequest.AnswerItem> pre = questionnaireByGranularTaskId.get(granularTaskId);
                questionnaireService.savePrefilledAnswers(workTaskId, granularTaskId, pre);
            }
            return true;
        }

        // Regular tasks: pick the best available user in the required role,
        // excluding the requestor (they cannot be assigned their own campaign's tasks).
        Optional<User> bestUser = userRepo.findBestAvailableUserInRole(roleId, requestorId);
        if (bestUser.isEmpty()) {
            log.warn("ROUTING assignTask — no user found | campaignId={} granularTaskId={} roleId={}",
                    campaignId, granularTaskId, roleId);
            return false;
        }

        User user = bestUser.get();

        WorkTask workTask = WorkTask.builder()
                .campaignId(campaignId)
                .assignedTo(user.getUserId().intValue())
                .granularTaskId(granularTaskId)
                .status(TaskStatus.ASSIGNED)
                .build();

        String workTaskId = workTaskRepo.insert(workTask);
        userRepo.incrementActiveTasks(user.getUserId());

        log.info("ROUTING assignTask | campaignId={} granularTaskId={} assigneeId={} assigneeName={} status=ASSIGNED",
                campaignId, granularTaskId, user.getUserId(), user.getFullName());

        // Auto-add the campaign requestor as a collaborator so they can chat
        // with the worker from day one without an explicit invite
        if (requestorId > 0) {
            collaboratorRepo.addSingleCollaborator(workTaskId, requestorId);
        }

        if (granularTaskId != null) {
            List<WorkTaskAnswerRequest.AnswerItem> pre = questionnaireByGranularTaskId.get(granularTaskId);
            questionnaireService.savePrefilledAnswers(workTaskId, granularTaskId, pre);
        }
        return true;
    }

    /** Internal record: where to send one deliverable. */
    private record RoutingTarget(String granularTaskId, String roleId) {}
}

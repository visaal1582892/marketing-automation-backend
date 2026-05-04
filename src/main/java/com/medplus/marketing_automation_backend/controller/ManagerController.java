package com.medplus.marketing_automation_backend.controller;

import com.medplus.marketing_automation_backend.domain.ApprovalLog;
import com.medplus.marketing_automation_backend.dto.ApprovalRequest;
import com.medplus.marketing_automation_backend.dto.UserResponse;
import com.medplus.marketing_automation_backend.dto.WorkTaskResponse;
import com.medplus.marketing_automation_backend.security.CustomUserDetails;
import com.medplus.marketing_automation_backend.service.ApprovalService;
import com.medplus.marketing_automation_backend.service.CampaignService;
import com.medplus.marketing_automation_backend.service.UserManagementService;
import com.medplus.marketing_automation_backend.service.WorkTaskService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/manager")
@PreAuthorize("hasAnyRole('ADMIN','MARKETING_MANAGER')")
public class ManagerController {

    private final CampaignService       campaignService;
    private final WorkTaskService       workTaskService;
    private final ApprovalService       approvalService;
    private final UserManagementService userManagementService;

    public ManagerController(CampaignService campaignService,
                             WorkTaskService workTaskService,
                             ApprovalService approvalService,
                             UserManagementService userManagementService) {
        this.campaignService       = campaignService;
        this.workTaskService       = workTaskService;
        this.approvalService       = approvalService;
        this.userManagementService = userManagementService;
    }

    // -------------------------------------------------------------------------
    // Task QC review
    // -------------------------------------------------------------------------

    /** All work tasks across all workers — Marketing Head overview with full filtering. */
    @GetMapping("/tasks/all")
    public List<WorkTaskResponse> allTasks() {
        return workTaskService.listAll();
    }

    @GetMapping("/tasks/review")
    public List<WorkTaskResponse> tasksForReview(@AuthenticationPrincipal CustomUserDetails principal) {
        int currentUserId = principal.getUser().getUserId().intValue();
        // Filter out tasks that were assigned to the reviewer themselves — they cannot self-approve.
        return workTaskService.listPendingQcReview().stream()
                .filter(t -> t.getAssignedTo() == null || t.getAssignedTo() != currentUserId)
                .collect(java.util.stream.Collectors.toList());
    }

    @PostMapping("/tasks/{taskId}/review")
    public WorkTaskResponse reviewTask(@PathVariable String taskId,
                                       @Valid @RequestBody ApprovalRequest req,
                                       @AuthenticationPrincipal CustomUserDetails principal) {
        return approvalService.review(taskId, req, principal.getUser());
    }

    @GetMapping("/tasks/{taskId}/history")
    public List<ApprovalLog> taskHistory(@PathVariable String taskId) {
        return approvalService.getHistory(taskId);
    }

    // -------------------------------------------------------------------------
    // Capacity dashboard
    // -------------------------------------------------------------------------

    /** Shows all active users with their workload (optionally filter by role). */
    @GetMapping("/capacity")
    public List<UserResponse> capacity(@RequestParam(required = false) String roleId) {
        if (roleId != null && !roleId.isBlank()) {
            return userManagementService.getCapacityByRole(roleId);
        }
        return userManagementService.list(false);
    }

    // -------------------------------------------------------------------------
    // Hold / Unhold (Module 2-B Capacity Alerts redesign)
    //
    // The marketing head holds a low-priority ASSIGNED task to free that
    // worker's slot for a higher-priority campaign waiting at the approval
    // gate. Held tasks live in their own tab where the manager can unhold
    // them — auto-routing then picks the next available user.
    // -------------------------------------------------------------------------

    /** Holds an ASSIGNED task — removes it from the worker's queue. */
    @PostMapping("/tasks/{taskId}/hold")
    public WorkTaskResponse holdTask(@PathVariable String taskId) {
        return campaignService.holdTask(taskId);
    }

    /** Lists every task currently on hold. */
    @GetMapping("/tasks/held")
    public List<WorkTaskResponse> heldTasks() {
        return campaignService.listHeldTasks();
    }

    /**
     * Unholds a task and re-routes it via auto-routing (picks the least-loaded
     * eligible user automatically).
     */
    @PostMapping("/tasks/{taskId}/unhold")
    public WorkTaskResponse unholdTask(@PathVariable String taskId) {
        return campaignService.unholdTask(taskId);
    }

    /**
     * Returns all active users eligible to work on a specific held task,
     * based on the task's granular-task type and role-task mappings.
     * Used by the manual-assign modal on the Held Tasks page.
     */
    @GetMapping("/tasks/{taskId}/eligible-users")
    public List<UserResponse> eligibleUsersForTask(@PathVariable String taskId) {
        return campaignService.getEligibleUsersForTask(taskId);
    }

    /**
     * Cancels an ASSIGNED or HELD task (i.e. one that has not yet been started
     * by the worker). Returns an error if the task is already IN_PROGRESS.
     */
    @PostMapping("/tasks/{taskId}/cancel")
    public WorkTaskResponse cancelTask(@PathVariable String taskId) {
        return campaignService.cancelTask(taskId);
    }

    /**
     * Manually assigns a held task to a specific user, bypassing auto-routing.
     * Body: { "userId": 7 }
     */
    @PostMapping("/tasks/{taskId}/assign")
    public WorkTaskResponse assignHeldTask(@PathVariable String taskId,
                                           @RequestBody java.util.Map<String, Object> body) {
        Object uidRaw = body == null ? null : body.get("userId");
        if (uidRaw == null) throw new IllegalArgumentException("userId is required");
        int userId = uidRaw instanceof Number n ? n.intValue() : Integer.parseInt(uidRaw.toString());
        return campaignService.assignHeldTaskToUser(taskId, userId);
    }

    // -------------------------------------------------------------------------
    // Module 3 — Time-tracking reports
    // -------------------------------------------------------------------------

    /**
     * Per-user efficiency summary for an optional date range.
     * Query params: ?from=YYYY-MM-DD&to=YYYY-MM-DD (both optional).
     */
    @GetMapping("/reports/time")
    public List<Map<String, Object>> timeReport(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        java.time.LocalDate fromDate = (from == null || from.isBlank()) ? null : java.time.LocalDate.parse(from);
        java.time.LocalDate toDate   = (to   == null || to.isBlank())   ? null : java.time.LocalDate.parse(to);
        return workTaskService.timeSummary(fromDate, toDate);
    }

    /**
     * Full analytics snapshot — powers the manager Reports dashboard.
     * Returns campaign counts, task counts, weekly trends, team performance,
     * and top rework offenders in a single call.
     */
    @GetMapping("/reports/analytics")
    public Map<String, Object> analyticsReport() {
        return workTaskService.analyticsSummary();
    }

    /**
     * Rework quality report — two views in one response:
     *
     *  byWorker : per-assignee rework totals + distinct tasks affected + last rework date.
     *             Use this to identify workers whose output consistently needs revision.
     *
     *  byTask   : every task that received ≥1 rework, with the rework count, last
     *             reviewer comment, and current task status.
     *             Use this to find tasks stuck in a rework loop.
     */
    @GetMapping("/reports/rework")
    public Map<String, Object> reworkReport() {
        return approvalService.getReworkReport();
    }
}

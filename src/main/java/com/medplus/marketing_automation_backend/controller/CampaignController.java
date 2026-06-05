package com.medplus.marketing_automation_backend.controller;

import com.medplus.marketing_automation_backend.dto.CampaignDashboardSummaryResponse;
import com.medplus.marketing_automation_backend.dto.CampaignRequest;
import com.medplus.marketing_automation_backend.dto.CampaignResponse;
import com.medplus.marketing_automation_backend.dto.CampaignUpdateRequest;
import com.medplus.marketing_automation_backend.dto.PagedResponse;
import com.medplus.marketing_automation_backend.dto.RequestorCampaignUpdateRequest;
import com.medplus.marketing_automation_backend.dto.FollowupTaskRequest;
import com.medplus.marketing_automation_backend.dto.TaskSpecRequest;
import com.medplus.marketing_automation_backend.dto.WorkTaskResponse;
import com.medplus.marketing_automation_backend.enums.Priority;
import com.medplus.marketing_automation_backend.security.CustomUserDetails;
import com.medplus.marketing_automation_backend.service.ApprovalService;
import com.medplus.marketing_automation_backend.service.CampaignService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/campaigns")
public class CampaignController {

    private final CampaignService  campaignService;
    private final ApprovalService  approvalService;

    public CampaignController(CampaignService campaignService,
                               ApprovalService approvalService) {
        this.campaignService = campaignService;
        this.approvalService = approvalService;
    }

    /** Create a new campaign request. Accessible by Admin, Requestor, Marketing Manager, Head, Regional Manager. */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','REQUESTOR','MARKETING_MANAGER','HEAD','REGIONAL_MANAGER')")
    public ResponseEntity<CampaignResponse> create(
            @Valid @RequestBody CampaignRequest req,
            @AuthenticationPrincipal CustomUserDetails principal) {
        CampaignResponse response = campaignService.create(req, principal.getUser());
        return ResponseEntity.status(201).body(response);
    }

    /** Paginated campaign list — always scoped to the caller's own requests. */
    @GetMapping
    public PagedResponse<CampaignResponse> list(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(required = false) String campaignId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        int requestorId = principal.getUser().getUserId().intValue();
        LocalDate from = dateFrom != null && !dateFrom.isBlank() ? LocalDate.parse(dateFrom) : null;
        LocalDate to   = dateTo   != null && !dateTo.isBlank()   ? LocalDate.parse(dateTo)   : null;
        return campaignService.listMyPaged(requestorId, campaignId, status, priority, taskType, from, to, page, size);
    }

    /**
     * Paged REQUESTOR_QC_REVIEW tasks for the caller's own campaigns.
     * Admins see all. Supports search, date-range, and pagination.
     */
    @GetMapping("/requestor-qc-tasks")
    @PreAuthorize("hasAnyRole('ADMIN','REQUESTOR','MARKETING_MANAGER','HEAD','REGIONAL_MANAGER')")
    public PagedResponse<WorkTaskResponse> requestorQcTasks(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        int requestorId = principal.getUser().getUserId().intValue();
        LocalDate from = dateFrom != null ? LocalDate.parse(dateFrom) : null;
        LocalDate to   = dateTo   != null ? LocalDate.parse(dateTo)   : null;
        // Always filter by caller's requestor_id — admin sees only their own campaigns' QC tasks
        return campaignService.listRequestorQcTasks(requestorId, false, search, from, to, page, size);
    }

    /**
     * Paginated COMPLETED work tasks for the caller's own campaigns.
     * Used by the requestor's "Completed Tasks" page.
     */
    @GetMapping("/completed-tasks")
    public PagedResponse<WorkTaskResponse> completedTasks(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(required = false) String campaignId,
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) String taskName,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String completedBy,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        int requestorId = principal.getUser().getUserId().intValue();
        LocalDate from = dateFrom != null && !dateFrom.isBlank() ? LocalDate.parse(dateFrom) : null;
        LocalDate to   = dateTo   != null && !dateTo.isBlank()   ? LocalDate.parse(dateTo)   : null;
        return campaignService.listCompletedTasksForRequestorPaged(
                requestorId, campaignId, taskId, taskName, taskType, completedBy,
                from, to, page, size);
    }

    /** Returns the caller's bookmarked campaigns. */
    @GetMapping("/bookmarked")
    public List<CampaignResponse> bookmarked(@AuthenticationPrincipal CustomUserDetails principal) {
        return campaignService.listBookmarked(principal.getUser().getUserId().intValue());
    }

    /**
     * Aggregated campaign status counts for the caller's own campaigns.
     * Powers the requestor dashboard KPI cards without loading the full campaign list.
     */
    @GetMapping("/dashboard-summary")
    public CampaignDashboardSummaryResponse dashboardSummary(
            @AuthenticationPrincipal CustomUserDetails principal) {
        int requestorId = principal.getUser().getUserId().intValue();
        return campaignService.getDashboardSummary(requestorId);
    }

    /**
     * Toggles a bookmark for the calling user on the given campaign.
     * Returns {@code { "bookmarked": true/false }}.
     */
    @PostMapping("/{id}/bookmark")
    public Map<String, Boolean> toggleBookmark(@PathVariable int id,
                                               @AuthenticationPrincipal CustomUserDetails principal) {
        boolean now = campaignService.toggleBookmark(
                principal.getUser().getUserId().intValue(), id);
        return Map.of("bookmarked", now);
    }

    /**
     * Clones a campaign — copies all brief fields into a new campaign owned by the caller.
     * No tasks are copied; the clone is a fresh request with status IN_PROGRESS.
     * Returns {@code { "campaignId": newId }}.
     */
    @PostMapping("/{id}/clone")
    @PreAuthorize("hasAnyRole('ADMIN','REQUESTOR','MARKETING_MANAGER','HEAD','REGIONAL_MANAGER')")
    public Map<String, Integer> cloneCampaign(@PathVariable int id,
                                               @AuthenticationPrincipal CustomUserDetails principal) {
        int newId = campaignService.cloneCampaign(id, principal.getUser().getUserId().intValue());
        return Map.of("campaignId", newId);
    }

    /** Get full campaign detail including deliverable specs and work tasks. */
    @GetMapping("/{id}")
    public CampaignResponse getById(@PathVariable int id,
                                    @AuthenticationPrincipal CustomUserDetails principal) {
        return campaignService.getDetail(id, principal.getUser().getUserId().intValue());
    }

    // -------------------------------------------------------------------------
    // Marketing Head adjustments — edit or reprioritize any non-terminal campaign
    // -------------------------------------------------------------------------

    /**
     * Requestor (owner) appends new task deliverables to an existing campaign,
     * including any task-specific questionnaire answers for the new tasks.
     * Existing tasks are never removed or re-routed.
     */
    @PostMapping("/{id}/add-tasks")
    @PreAuthorize("hasAnyRole('ADMIN','REQUESTOR','MARKETING_MANAGER','HEAD','REGIONAL_MANAGER')")
    public CampaignResponse addTasks(@PathVariable int id,
                                     @RequestBody List<TaskSpecRequest> specs,
                                     @AuthenticationPrincipal CustomUserDetails principal) {
        return campaignService.addTasksToCampaign(id, specs, principal.getUser().getUserId().intValue());
    }

    @PostMapping("/{id}/followup-tasks")
    @PreAuthorize("hasAnyRole('ADMIN','REQUESTOR')")
    public CampaignResponse addFollowupTasks(@PathVariable int id,
                                              @RequestBody FollowupTaskRequest req,
                                              @AuthenticationPrincipal CustomUserDetails principal) {
        return campaignService.addFollowupTasks(id, req, principal.getUser().getUserId().intValue());
    }

    /**
     * Requestor (owner) edits the form fields of their own campaign and
     * optionally adds new task deliverables or campaign-level files.
     * Existing tasks and files are never removed or modified.
     * Not allowed on terminal campaigns (COMPLETED / REJECTED / CANCELLED).
     */
    @PutMapping("/{id}/requestor-edit")
    @PreAuthorize("hasAnyRole('ADMIN','REQUESTOR','MARKETING_MANAGER','HEAD','REGIONAL_MANAGER')")
    public CampaignResponse requestorEdit(@PathVariable int id,
                                           @RequestBody RequestorCampaignUpdateRequest req,
                                           @AuthenticationPrincipal CustomUserDetails principal) {
        return campaignService.editCampaignAsRequestor(
                id, req, principal.getUser().getUserId().intValue());
    }

    /**
     * Deletes a single work task from a campaign.
     * The campaign requestor or a manager/admin may call this.
     * Deletion is blocked when the task has already been started
     * (IN_PROGRESS, REWORK, MANAGER_QC_REVIEW, COMPLETED).
     */
    @DeleteMapping("/{id}/tasks/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN','REQUESTOR','MARKETING_MANAGER','HEAD','REGIONAL_MANAGER')")
    public ResponseEntity<Void> deleteTask(@PathVariable int id,
                                           @PathVariable String taskId,
                                           @AuthenticationPrincipal CustomUserDetails principal) {
        boolean isManager = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().matches(
                        "ROLE_ADMIN|ROLE_MARKETING_MANAGER|ROLE_HEAD|ROLE_REGIONAL_MANAGER"));
        campaignService.requestorDeleteTask(id, taskId,
                principal.getUser().getUserId().intValue(), isManager);
        return ResponseEntity.noContent().build();
    }

    /**
     * Deletes an entire campaign.  Only the campaign requestor may call this,
     * and only when none of the tasks has been started.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','REQUESTOR','MARKETING_MANAGER','HEAD','REGIONAL_MANAGER')")
    public ResponseEntity<Void> deleteCampaign(@PathVariable int id,
                                               @AuthenticationPrincipal CustomUserDetails principal) {
        campaignService.requestorDeleteCampaign(id, principal.getUser().getUserId().intValue());
        return ResponseEntity.noContent().build();
    }

    /**
     * Allows the Marketing Head (or Admin) to edit key fields of a campaign
     * at any non-terminal stage: priority, key message, and budget tier.
     * The inconsistency flag is recomputed server-side.
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MARKETING_MANAGER')")
    public CampaignResponse updateCampaign(@PathVariable int id,
                                            @RequestBody CampaignUpdateRequest req) {
        return campaignService.updateCampaign(id, req);
    }

    /**
     * Allows the Marketing Head (or Admin) to change the priority of a
     * campaign at any non-terminal stage. Worker queues automatically re-rank
     * because they sort on the live {@code campaigns.priority} value; the
     * inconsistency flag is recomputed server-side.
     */
    @PatchMapping("/{id}/priority")
    @PreAuthorize("hasAnyRole('ADMIN','MARKETING_MANAGER')")
    public CampaignResponse updatePriority(@PathVariable int id,
                                            @RequestBody Map<String, String> body) {
        String raw = body == null ? null : body.get("priority");
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Field 'priority' is required");
        }
        Priority priority;
        try {
            priority = Priority.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown priority value: " + raw);
        }
        return campaignService.updatePriority(id, priority);
    }

    // -------------------------------------------------------------------------
    // Requestor rework — send a REQUESTOR_QC_REVIEW task back for rework
    // -------------------------------------------------------------------------

    /**
     * Allows the campaign requestor (or Admin) to send a task in REQUESTOR_QC_REVIEW
     * back for rework, if they are not satisfied with the output.
     * The task is flipped REQUESTOR_QC_REVIEW → REWORK and the parent campaign is
     * re-opened.
     *
     * <p>Body: { "message": "Please revise the colour scheme." }
     */
    @PostMapping("/{campaignId}/tasks/{taskId}/requestor-rework")
    @PreAuthorize("hasAnyRole('ADMIN','REQUESTOR','MARKETING_MANAGER','HEAD','REGIONAL_MANAGER')")
    public WorkTaskResponse requestorRework(@PathVariable int campaignId,
                                             @PathVariable String taskId,
                                             @RequestBody(required = false) Map<String, String> body,
                                             @AuthenticationPrincipal CustomUserDetails principal) {
        String message = body != null ? body.get("message") : null;
        return approvalService.requestorReworkTask(campaignId, taskId, message, principal.getUser());
    }

    // -------------------------------------------------------------------------
    // Requestor approval — sign off on a REQUESTOR_QC_REVIEW task
    // -------------------------------------------------------------------------

    /**
     * Allows the campaign requestor (or Admin) to approve a task in REQUESTOR_QC_REVIEW.
     * The task moves to COMPLETED and requestor_approved_at is stamped.
     * If all tasks are done, the campaign is marked COMPLETED.
     *
     * <p>Body: { "comment": "Looks great!" } (optional)
     */
    @PostMapping("/{campaignId}/tasks/{taskId}/requestor-approve")
    @PreAuthorize("hasAnyRole('ADMIN','REQUESTOR','MARKETING_MANAGER','HEAD','REGIONAL_MANAGER')")
    public WorkTaskResponse requestorApprove(@PathVariable int campaignId,
                                              @PathVariable String taskId,
                                              @RequestBody(required = false) Map<String, String> body,
                                              @AuthenticationPrincipal CustomUserDetails principal) {
        String comment = body != null ? body.get("comment") : null;
        return approvalService.requestorApproveTask(campaignId, taskId, comment, principal.getUser());
    }
}

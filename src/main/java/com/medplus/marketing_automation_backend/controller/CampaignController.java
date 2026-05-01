package com.medplus.marketing_automation_backend.controller;

import com.medplus.marketing_automation_backend.dto.CampaignRequest;
import com.medplus.marketing_automation_backend.dto.CampaignResponse;
import com.medplus.marketing_automation_backend.dto.CampaignUpdateRequest;
import com.medplus.marketing_automation_backend.dto.RequestorCampaignUpdateRequest;
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

    /** List campaigns: admins/managers see all; others see only their own. */
    @GetMapping
    public List<CampaignResponse> list(
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @AuthenticationPrincipal CustomUserDetails principal) {
        boolean isAdminOrManager = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_MARKETING_MANAGER"));
        if (isAdminOrManager) {
            return campaignService.listAll(includeInactive);
        }
        return campaignService.listMy(principal.getUser().getUserId().intValue());
    }

    /** Get full campaign detail including deliverable specs and work tasks. */
    @GetMapping("/{id}")
    public CampaignResponse getById(@PathVariable int id) {
        return campaignService.getDetail(id);
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
     * Deletes a single task spec from a campaign.
     * Only the campaign requestor may call this, and only for tasks that have
     * not yet been started (status ASSIGNED, HELD, or ACCEPTED).
     */
    @DeleteMapping("/{id}/deliverables/{specId}")
    @PreAuthorize("hasAnyRole('ADMIN','REQUESTOR','MARKETING_MANAGER','HEAD','REGIONAL_MANAGER')")
    public ResponseEntity<Void> deleteTask(@PathVariable int id,
                                           @PathVariable int specId,
                                           @AuthenticationPrincipal CustomUserDetails principal) {
        campaignService.requestorDeleteTask(id, specId, principal.getUser().getUserId().intValue());
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
    // Requestor rework — send a COMPLETED task back for another rework cycle
    // -------------------------------------------------------------------------

    /**
     * Allows the campaign requestor (or Admin) to send a COMPLETED task back
     * for rework after delivery, if they are not satisfied with the output.
     * The task is flipped COMPLETED → REWORK and the parent campaign is
     * re-opened if it had been fully completed.
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
}

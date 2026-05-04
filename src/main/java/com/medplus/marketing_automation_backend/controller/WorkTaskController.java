package com.medplus.marketing_automation_backend.controller;

import com.medplus.marketing_automation_backend.dto.TaskSubmissionRequest;
import com.medplus.marketing_automation_backend.dto.WorkTaskResponse;
import com.medplus.marketing_automation_backend.security.CustomUserDetails;
import com.medplus.marketing_automation_backend.service.WorkTaskService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class WorkTaskController {

    private final WorkTaskService workTaskService;

    public WorkTaskController(WorkTaskService workTaskService) {
        this.workTaskService = workTaskService;
    }

    /** List all tasks assigned to the currently authenticated user. */
    @GetMapping("/my")
    public List<WorkTaskResponse> listMy(@AuthenticationPrincipal CustomUserDetails principal) {
        return workTaskService.listMy(principal.getUser().getUserId().intValue());
    }

    /** Get a specific task (must belong to the authenticated user). */
    @GetMapping("/{id}")
    public WorkTaskResponse get(@PathVariable String id,
                                @AuthenticationPrincipal CustomUserDetails principal) {
        return workTaskService.get(id, principal.getUser().getUserId().intValue());
    }

    /**
     * Accept a task — starts the timer (status → IN_PROGRESS).
     * Task must be ASSIGNED or REWORK.
     */
    @PatchMapping("/{id}/accept")
    public WorkTaskResponse accept(@PathVariable String id,
                                   @AuthenticationPrincipal CustomUserDetails principal) {
        return workTaskService.accept(id, principal.getUser().getUserId().intValue());
    }

    /**
     * Mark a task as complete — stops the timer (status → QC_REVIEW).
     * Task must be IN_PROGRESS.
     *
     * Body may contain:
     *  - submissionNotes : free-text notes for the QC reviewer.
     *  - assetUrls       : list of file URLs uploaded via POST /api/upload/asset.
     *                      Stored in the asset_info table (one row per URL).
     *  - assetUrl        : legacy single-URL field; used only when assetUrls is absent.
     */
    @PatchMapping("/{id}/complete")
    public WorkTaskResponse complete(@PathVariable String id,
                                     @RequestBody(required = false) TaskSubmissionRequest body,
                                     @AuthenticationPrincipal CustomUserDetails principal) {
        String notes = body == null ? null : body.getSubmissionNotes();
        List<String> assetUrls = resolveAssetUrls(body);
        return workTaskService.complete(id, principal.getUser().getUserId().intValue(), notes, assetUrls);
    }

    /**
     * Worker adds a comment on their task and self-holds it.
     * Body: { "comment": "..." }
     */
    @PostMapping("/{id}/comment")
    public WorkTaskResponse addCommentAndHold(@PathVariable String id,
                                              @RequestBody java.util.Map<String, String> body,
                                              @AuthenticationPrincipal CustomUserDetails principal) {
        String comment = body == null ? null : body.get("comment");
        return workTaskService.addCommentAndHold(id, principal.getUser().getUserId().intValue(), comment);
    }

    /**
     * Worker clears their comment and resumes the task (status → ASSIGNED).
     */
    @PatchMapping("/{id}/worker-unhold")
    public WorkTaskResponse workerUnhold(@PathVariable String id,
                                         @AuthenticationPrincipal CustomUserDetails principal) {
        return workTaskService.workerUnhold(id, principal.getUser().getUserId().intValue());
    }

    /** Returns a flat list of asset URLs from the request body. */
    @SuppressWarnings("deprecation")
    private List<String> resolveAssetUrls(TaskSubmissionRequest body) {
        if (body == null) return List.of();
        List<String> urls = body.getAssetUrls();
        if (urls != null && !urls.isEmpty()) return urls;
        // Legacy single-URL field
        String single = body.getAssetUrl();
        return (single != null && !single.isBlank()) ? List.of(single) : List.of();
    }
}

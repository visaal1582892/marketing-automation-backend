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
     *                      Serialised as a JSON array and stored in work_tasks.asset_url.
     *  - assetUrl        : legacy single-URL field; used only when assetUrls is absent.
     */
    @PatchMapping("/{id}/complete")
    public WorkTaskResponse complete(@PathVariable String id,
                                     @RequestBody(required = false) TaskSubmissionRequest body,
                                     @AuthenticationPrincipal CustomUserDetails principal) {
        String notes    = body == null ? null : body.getSubmissionNotes();
        String assetUrl = resolveAssetUrl(body);
        return workTaskService.complete(id, principal.getUser().getUserId().intValue(), notes, assetUrl);
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

    /**
     * Converts the submission's asset references into a single string for storage.
     * Multiple URLs → JSON array string  (e.g. ["url1","url2"])
     * Single URL    → the URL itself     (backward-compatible plain string)
     * None          → null
     */
    @SuppressWarnings("deprecation")
    private String resolveAssetUrl(TaskSubmissionRequest body) {
        if (body == null) return null;

        List<String> urls = body.getAssetUrls();
        if (urls != null && !urls.isEmpty()) {
            if (urls.size() == 1) return urls.get(0);
            return toJsonArray(urls);
        }
        return body.getAssetUrl(); // legacy field
    }

    /** Serialises a list of URL strings to a JSON array without requiring Jackson. */
    private static String toJsonArray(List<String> urls) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < urls.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append('"').append(urls.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return sb.append(']').toString();
    }
}

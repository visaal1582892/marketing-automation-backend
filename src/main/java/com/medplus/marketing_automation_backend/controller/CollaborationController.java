package com.medplus.marketing_automation_backend.controller;

import com.medplus.marketing_automation_backend.domain.AssetInfo;
import com.medplus.marketing_automation_backend.domain.TaskCollaborator;
import com.medplus.marketing_automation_backend.domain.TaskMessage;
import com.medplus.marketing_automation_backend.domain.User;
import com.medplus.marketing_automation_backend.dto.WorkTaskResponse;
import com.medplus.marketing_automation_backend.security.CustomUserDetails;
import com.medplus.marketing_automation_backend.service.CollaborationService;
import com.medplus.marketing_automation_backend.service.TaskMessageService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/collaborations")
public class CollaborationController {

    private final CollaborationService   collaborationService;
    private final TaskMessageService     taskMessageService;
    private final SimpMessagingTemplate  messagingTemplate;

    public CollaborationController(CollaborationService collaborationService,
                                   TaskMessageService taskMessageService,
                                   SimpMessagingTemplate messagingTemplate) {
        this.collaborationService = collaborationService;
        this.taskMessageService   = taskMessageService;
        this.messagingTemplate    = messagingTemplate;
    }

    // ── Collaboration lifecycle ───────────────────────────────────────────────

    /**
     * Start collaboration: auto-holds the task and ensures the requestor is a collaborator.
     * Only the assigned worker may call this.
     */
    @PostMapping("/{taskId}/start")
    public org.springframework.http.ResponseEntity<Void> start(
            @PathVariable String taskId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        collaborationService.startCollaboration(taskId, principal.getUser().getUserId().intValue());
        return org.springframework.http.ResponseEntity.ok().build();
    }

    /**
     * Pause collaboration: restores the task to its pre-hold status so it re-enters the queue.
     * The assigned worker or an admin may call this.
     */
    @PostMapping("/{taskId}/pause")
    public org.springframework.http.ResponseEntity<Void> pause(
            @PathVariable String taskId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        collaborationService.pauseCollaboration(taskId, principal.getUser().getUserId().intValue());
        return org.springframework.http.ResponseEntity.ok().build();
    }

    // ── Collaborator management ───────────────────────────────────────────────

    /**
     * Invite collaborators to a task.
     * Body: { "userIds": [1, 2, 3] }
     * Only the task's assigned worker may call this.
     */
    @PostMapping("/{taskId}/invite")
    public List<TaskCollaborator> invite(
            @PathVariable String taskId,
            @RequestBody Map<String, List<Integer>> body,
            @AuthenticationPrincipal CustomUserDetails principal) {
        List<Integer> userIds = body.get("userIds");
        return collaborationService.addCollaborators(
                taskId, principal.getUser().getUserId().intValue(), userIds);
    }

    /**
     * List all collaborators on a task.
     * Caller must be the worker or a collaborator.
     */
    @GetMapping("/{taskId}/members")
    public List<TaskCollaborator> getMembers(
            @PathVariable String taskId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return collaborationService.getCollaborators(
                taskId, principal.getUser().getUserId().intValue());
    }

    /**
     * List all tasks on which the caller is a collaborator (newest first).
     */
    @GetMapping("/my")
    public List<WorkTaskResponse> myCollaborations(
            @AuthenticationPrincipal CustomUserDetails principal) {
        return collaborationService.getMyCollaborations(
                principal.getUser().getUserId().intValue());
    }

    /**
     * All active users — used to populate the collaborator picker.
     */
    @GetMapping("/users")
    public List<User> allUsers() {
        return collaborationService.getAllUsers();
    }

    // ── Chat ──────────────────────────────────────────────────────────────────

    /**
     * Send a chat message. Persists to DB and broadcasts via STOMP.
     * Body: { "message": "..." }
     * Blocked when task is COMPLETED.
     */
    @PostMapping("/{taskId}/messages")
    public TaskMessage sendMessage(
            @PathVariable String taskId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails principal) {
        String text = body == null ? null : body.get("message");
        TaskMessage saved = taskMessageService.sendMessage(
                taskId, principal.getUser().getUserId().intValue(), text);
        // Broadcast to all WebSocket subscribers of this task's chat topic
        messagingTemplate.convertAndSend("/topic/chat/" + taskId, (Object) saved);
        return saved;
    }

    /**
     * Get all chat messages for a task (used for initial history load).
     * Caller must be the worker or a collaborator.
     */
    @GetMapping("/{taskId}/messages")
    public List<TaskMessage> getMessages(
            @PathVariable String taskId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return taskMessageService.getMessages(
                taskId, principal.getUser().getUserId().intValue());
    }

    // ── Typing indicator ──────────────────────────────────────────────────────

    /**
     * Relays a typing event from one collaborator to all others on the task.
     * The client sends { "isTyping": true/false } to /app/typing/{taskId}.
     * This method broadcasts { userId, userName, isTyping } to /topic/typing/{taskId}.
     */
    @MessageMapping("/typing/{taskId}")
    public void relayTyping(
            @DestinationVariable String taskId,
            Map<String, Object> payload,
            @AuthenticationPrincipal CustomUserDetails principal) {
        if (principal == null) return;
        Map<String, Object> event = new java.util.HashMap<>();
        event.put("userId",   principal.getUser().getUserId());
        event.put("userName", principal.getUser().getFullName());
        event.put("isTyping", payload != null && Boolean.TRUE.equals(payload.get("isTyping")));
        // Cast to Object avoids ambiguity between SimpMessagingTemplate overloads
        messagingTemplate.convertAndSend("/topic/typing/" + taskId, (Object) event);
    }

    // ── Assets ────────────────────────────────────────────────────────────────

    /**
     * Add an asset URL to a task.
     * Body: { "url": "https://...", "thumbnailUrl": "https://...", "originalFilename": "report.docx" }
     * Caller must be the worker or a collaborator.
     */
    @PostMapping("/{taskId}/assets")
    public AssetInfo addAsset(
            @PathVariable String taskId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails principal) {
        String url              = body == null ? null : body.get("url");
        String thumbnailUrl     = body == null ? null : body.get("thumbnailUrl");
        String originalFilename = body == null ? null : body.get("originalFilename");
        return collaborationService.addAsset(
                taskId, principal.getUser().getUserId().intValue(), url, thumbnailUrl, originalFilename);
    }

    /**
     * Delete an asset. Only the uploader may delete their own asset.
     */
    @DeleteMapping("/{taskId}/assets/{assetId}")
    public org.springframework.http.ResponseEntity<Void> deleteAsset(
            @PathVariable String taskId,
            @PathVariable int assetId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        collaborationService.deleteAsset(assetId, principal.getUser().getUserId().intValue());
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    /**
     * List all assets for a task.
     * Caller must be the worker or a collaborator.
     */
    @GetMapping("/{taskId}/assets")
    public List<AssetInfo> getAssets(
            @PathVariable String taskId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return collaborationService.getAssets(
                taskId, principal.getUser().getUserId().intValue());
    }
}

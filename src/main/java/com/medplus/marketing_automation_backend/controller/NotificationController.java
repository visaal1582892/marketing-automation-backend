package com.medplus.marketing_automation_backend.controller;

import com.medplus.marketing_automation_backend.domain.Notification;
import com.medplus.marketing_automation_backend.domain.NotificationTemplate;
import com.medplus.marketing_automation_backend.security.CustomUserDetails;
import com.medplus.marketing_automation_backend.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // ── User-facing ───────────────────────────────────────────────────────────

    /** Returns the most recent 100 notifications for the authenticated user. */
    @GetMapping
    public List<Notification> getMyNotifications(
            @AuthenticationPrincipal CustomUserDetails principal) {
        return notificationService.getNotifications(principal.getUser().getUserId());
    }

    /** Returns only the unread count — lightweight poll alternative. */
    @GetMapping("/unread-count")
    public Map<String, Integer> getUnreadCount(
            @AuthenticationPrincipal CustomUserDetails principal) {
        int count = notificationService.getUnreadCount(principal.getUser().getUserId());
        return Map.of("count", count);
    }

    /** Marks a single notification as read. */
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(
            @PathVariable long id,
            @AuthenticationPrincipal CustomUserDetails principal) {
        notificationService.markRead(id, principal.getUser().getUserId());
        return ResponseEntity.noContent().build();
    }

    /** Marks ALL unread notifications as read for the authenticated user. */
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead(
            @AuthenticationPrincipal CustomUserDetails principal) {
        notificationService.markAllRead(principal.getUser().getUserId());
        return ResponseEntity.noContent().build();
    }

    // ── Admin: template management ────────────────────────────────────────────

    /**
     * List notification templates (Admin / Marketing Manager only).
     * Without page/size: full list. With page/size: PagedResponse for admin table.
     */
    @GetMapping("/templates")
    @PreAuthorize("hasAnyRole('ADMIN','MARKETING_MANAGER')")
    public Object getTemplates(@RequestParam(required = false) String id,
                               @RequestParam(required = false) String eventType,
                               @RequestParam(required = false) String appliesTo,
                               @RequestParam(required = false) String message,
                               @RequestParam(required = false) String url,
                               @RequestParam(required = false) Integer page,
                               @RequestParam(required = false) Integer size) {
        if (page != null || size != null) {
            return notificationService.getTemplatesPaged(
                    id, eventType, appliesTo, message, url,
                    page != null ? page : 0,
                    size != null ? size : 20);
        }
        return notificationService.getAllTemplates();
    }

    /**
     * Updates the message and/or URL template for a given template record.
     * Body: { "messageTemplate": "...", "urlTemplate": "..." }
     */
    @PutMapping("/templates/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MARKETING_MANAGER')")
    public NotificationTemplate updateTemplate(
            @PathVariable long id,
            @RequestBody Map<String, String> body) {
        String msg = body.get("messageTemplate");
        String url = body.get("urlTemplate");
        if (msg == null || msg.isBlank()) {
            throw new IllegalArgumentException("messageTemplate must not be blank");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("urlTemplate must not be blank");
        }
        return notificationService.updateTemplate(id, msg.trim(), url.trim());
    }
}

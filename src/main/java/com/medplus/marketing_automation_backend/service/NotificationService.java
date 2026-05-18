package com.medplus.marketing_automation_backend.service;

import com.medplus.marketing_automation_backend.domain.Notification;
import com.medplus.marketing_automation_backend.domain.NotificationTemplate;
import com.medplus.marketing_automation_backend.domain.User;
import com.medplus.marketing_automation_backend.event.CollaboratorAddedEvent;
import com.medplus.marketing_automation_backend.event.NewTaskMessageEvent;
import com.medplus.marketing_automation_backend.event.TaskAssignedEvent;
import com.medplus.marketing_automation_backend.event.TaskSubmittedForQcEvent;
import com.medplus.marketing_automation_backend.event.ManagerQcApprovedEvent;
import com.medplus.marketing_automation_backend.event.RequestorQcApprovedEvent;
import com.medplus.marketing_automation_backend.event.TaskHeldByManagerEvent;
import com.medplus.marketing_automation_backend.event.TaskCancelledEvent;
import com.medplus.marketing_automation_backend.event.CampaignDeletedEvent;
import com.medplus.marketing_automation_backend.event.CommentAddedEvent;
import com.medplus.marketing_automation_backend.event.CommentRespondedEvent;
import com.medplus.marketing_automation_backend.event.ManagerReworkEvent;
import com.medplus.marketing_automation_backend.event.RequestorReworkEvent;
import com.medplus.marketing_automation_backend.event.ManagerRejectEvent;
import com.medplus.marketing_automation_backend.event.ContentTaskSubmittedEvent;
import com.medplus.marketing_automation_backend.event.ContentTaskAutoClosedEvent;
import com.medplus.marketing_automation_backend.repository.CollaboratorRepository;
import com.medplus.marketing_automation_backend.repository.NotificationRepository;
import com.medplus.marketing_automation_backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Central notification hub.
 *
 * <p>Listens to domain events published by service layer, resolves the
 * appropriate template for each recipient's role, persists a {@link Notification}
 * row, and immediately pushes it over STOMP to
 * {@code /topic/notifications/{userId}} so the frontend can update in real time.
 *
 * <p>All listeners run in {@link TransactionPhase#AFTER_COMMIT} so they never
 * fire on a rolled-back transaction, and each opens a fresh
 * {@link Propagation#REQUIRES_NEW} transaction for the notification inserts.
 */
@Slf4j
@Service
public class NotificationService {

    // Role IDs from seed data (V2) — used to find manager recipients
    private static final String ROLE_MARKETING_MANAGER   = "13";
    private static final String ROLE_PROCUREMENT_MANAGER = "9";
    private static final String ROLE_REQUESTOR           = "12";

    private final NotificationRepository notificationRepo;
    private final UserRepository         userRepo;
    private final CollaboratorRepository collaboratorRepo;
    private final SimpMessagingTemplate  messagingTemplate;

    public NotificationService(NotificationRepository notificationRepo,
                               UserRepository userRepo,
                               CollaboratorRepository collaboratorRepo,
                               SimpMessagingTemplate messagingTemplate) {
        this.notificationRepo  = notificationRepo;
        this.userRepo          = userRepo;
        this.collaboratorRepo  = collaboratorRepo;
        this.messagingTemplate = messagingTemplate;
    }

    // =========================================================================
    // Event listeners
    // =========================================================================

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTaskAssigned(TaskAssignedEvent event) {
        send("TASK_ASSIGNED",
             event.assignedUserId(),
             null,
             Map.of("taskId", event.taskId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCollaboratorAdded(CollaboratorAddedEvent event) {
        Map<String, String> vars = Map.of(
                "taskId",      event.taskId(),
                "inviterName", event.inviterName());
        for (int uid : event.userIds()) {
            send("ADDED_TO_COLLABORATION", uid, null, vars);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onNewTaskMessage(NewTaskMessageEvent event) {
        Map<String, String> vars = Map.of(
                "taskId",     event.taskId(),
                "senderName", event.senderName());
        // Notify all collaborators except the message sender
        List<Integer> collaboratorIds = collaboratorRepo.findUserIdsByTaskId(event.taskId());
        for (int uid : collaboratorIds) {
            if (uid != event.senderId()) {
                send("NEW_TASK_MESSAGE", uid, null, vars);
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTaskSubmittedForQc(TaskSubmittedForQcEvent event) {
        Map<String, String> vars = Map.of(
                "taskId",     event.taskId(),
                "workerName", event.workerName());
        // Notify all Marketing Managers and Procurement Managers
        List<User> managers = userRepo.findByRole(ROLE_MARKETING_MANAGER);
        managers.addAll(userRepo.findByRole(ROLE_PROCUREMENT_MANAGER));
        for (User m : managers) {
            if (m.getUserId() != null && m.getUserId().intValue() != event.workerId()) {
                send("SUBMITTED_FOR_QC", m.getUserId().intValue(), resolveRoleId(m), vars);
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onManagerQcApproved(ManagerQcApprovedEvent event) {
        Map<String, String> vars = Map.of(
                "taskId",      event.taskId(),
                "managerName", event.managerName());

        // Notify the worker (default template → "Awaiting requestor sign-off")
        send("MANAGER_QC_APPROVAL", event.workerId(), null, vars);

        // Notify the requestor (role-specific template → "Ready for your review")
        if (event.requestorId() > 0 && event.requestorId() != event.workerId()) {
            send("MANAGER_QC_APPROVAL", event.requestorId(), ROLE_REQUESTOR, vars);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRequestorQcApproved(RequestorQcApprovedEvent event) {
        send("REQUESTOR_QC_APPROVAL",
             event.workerId(),
             null,
             Map.of("taskId", event.taskId(), "requestorName", event.requestorName()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTaskHeldByManager(TaskHeldByManagerEvent event) {
        send("TASK_HELD_BY_MANAGER",
             event.assignedUserId(),
             null,
             Map.of("taskId", event.taskId(), "managerName", event.managerName()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTaskCancelled(TaskCancelledEvent event) {
        send("TASK_CANCELLED",
             event.assignedUserId(),
             null,
             Map.of("taskId", event.taskId(), "managerName", event.managerName()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCampaignDeleted(CampaignDeletedEvent event) {
        Map<String, String> vars = Map.of(
                "campaignId",   String.valueOf(event.campaignId()),
                "campaignName", event.campaignName());
        for (int uid : event.assignedUserIds()) {
            send("CAMPAIGN_DELETED", uid, null, vars);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCommentAdded(CommentAddedEvent event) {
        send("COMMENT_ADDED",
             event.requestorId(),
             null,
             Map.of("taskId", event.taskId(), "workerName", event.workerName()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCommentResponded(CommentRespondedEvent event) {
        send("COMMENT_RESPONDED",
             event.assignedUserId(),
             null,
             Map.of("taskId", event.taskId(), "responderName", event.responderName()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onManagerRework(ManagerReworkEvent event) {
        send("MANAGER_REWORK",
             event.assignedUserId(),
             null,
             Map.of("taskId", event.taskId(), "managerName", event.managerName()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRequestorRework(RequestorReworkEvent event) {
        send("REQUESTOR_REWORK",
             event.assignedUserId(),
             null,
             Map.of("taskId", event.taskId(), "requestorName", event.requestorName()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onManagerReject(ManagerRejectEvent event) {
        send("MANAGER_REJECT",
             event.assignedUserId(),
             null,
             Map.of("taskId", event.taskId(), "managerName", event.managerName()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onContentTaskSubmitted(ContentTaskSubmittedEvent event) {
        send("CONTENT_TASK_SUBMITTED",
             event.designerUserId(),
             null,
             Map.of("taskId", event.contentTaskId(), "writerName", event.writerName()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onContentTaskAutoClosed(ContentTaskAutoClosedEvent event) {
        send("CONTENT_TASK_AUTO_CLOSED",
             event.assignedUserId(),
             null,
             Map.of("taskId", event.contentTaskId()));
    }

    // =========================================================================
    // Query helpers
    // =========================================================================

    public List<Notification> getNotifications(long userId) {
        return notificationRepo.findByUserId(userId);
    }

    public int getUnreadCount(long userId) {
        return notificationRepo.countUnreadByUserId(userId);
    }

    public void markRead(long notificationId, long userId) {
        notificationRepo.markRead(notificationId, userId);
    }

    public void markAllRead(long userId) {
        notificationRepo.markAllRead(userId);
    }

    public List<NotificationTemplate> getAllTemplates() {
        return notificationRepo.findAllTemplates();
    }

    public NotificationTemplate updateTemplate(long id, String messageTemplate, String urlTemplate) {
        notificationRepo.updateTemplate(id, messageTemplate, urlTemplate);
        return notificationRepo.findAllTemplates().stream()
                .filter(t -> t.getId().equals(id))
                .findFirst()
                .orElseThrow();
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /**
     * Resolves the right template, applies variable substitution, saves the
     * notification row, and pushes it via STOMP.
     *
     * @param eventType  e.g. "TASK_ASSIGNED"
     * @param userId     recipient
     * @param roleId     caller-supplied role override (may be null — triggers role lookup)
     * @param vars       placeholder map used for template substitution
     */
    private void send(String eventType, int userId, String roleId, Map<String, String> vars) {
        try {
            String effectiveRole = roleId != null ? roleId : resolveUserRole(userId);
            NotificationTemplate tmpl = resolveTemplate(eventType, effectiveRole);
            if (tmpl == null) {
                log.warn("No template found for eventType={} roleId={}", eventType, effectiveRole);
                return;
            }
            String message = applyVars(tmpl.getMessageTemplate(), vars);
            String url     = applyVars(tmpl.getUrlTemplate(), vars);

            Notification saved = notificationRepo.insert(userId, eventType, message, url);

            // Real-time push — fires immediately after DB commit
            messagingTemplate.convertAndSend(
                    "/topic/notifications/" + userId, (Object) saved);

            log.debug("Notification sent | userId={} event={} id={}", userId, eventType, saved.getId());
        } catch (Exception ex) {
            log.error("Failed to send notification | userId={} event={}", userId, eventType, ex);
        }
    }

    /**
     * Resolves the best matching template.
     * Priority: role-specific > default.
     */
    private NotificationTemplate resolveTemplate(String eventType, String roleId) {
        Optional<NotificationTemplate> specific =
                notificationRepo.findTemplateByEventAndRole(eventType, roleId);
        if (specific.isPresent()) return specific.get();
        return notificationRepo.findDefaultTemplate(eventType).orElse(null);
    }

    /** Returns the first role_id for the user (used when no explicit override given). */
    private String resolveUserRole(int userId) {
        try {
            User u = userRepo.findById((long) userId).orElse(null);
            if (u == null || u.getRoleIds() == null || u.getRoleIds().isEmpty()) return null;
            return u.getRoleIds().get(0);
        } catch (Exception ex) {
            return null;
        }
    }

    /** Returns any role id of the user — just the first for template lookup. */
    private String resolveRoleId(User user) {
        if (user.getRoleIds() == null || user.getRoleIds().isEmpty()) return null;
        return user.getRoleIds().get(0);
    }

    /** Replaces {key} placeholders in the template string with values from the map. */
    private String applyVars(String template, Map<String, String> vars) {
        if (template == null) return "";
        String result = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }
}

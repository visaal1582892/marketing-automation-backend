package com.medplus.marketing_automation_backend.service;

import com.medplus.marketing_automation_backend.domain.AssetInfo;
import com.medplus.marketing_automation_backend.domain.TaskCollaborator;
import com.medplus.marketing_automation_backend.domain.User;
import com.medplus.marketing_automation_backend.domain.WorkTask;
import com.medplus.marketing_automation_backend.dto.WorkTaskResponse;
import com.medplus.marketing_automation_backend.exception.BadRequestException;
import com.medplus.marketing_automation_backend.exception.ResourceNotFoundException;
import com.medplus.marketing_automation_backend.repository.AssetInfoRepository;
import com.medplus.marketing_automation_backend.repository.CollaboratorRepository;
import com.medplus.marketing_automation_backend.repository.UserRepository;
import com.medplus.marketing_automation_backend.repository.WorkTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.net.ssl.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CollaborationService {

    private final CollaboratorRepository collaboratorRepo;
    private final WorkTaskRepository     workTaskRepo;
    private final AssetInfoRepository    assetInfoRepo;
    private final UserRepository         userRepo;

    public CollaborationService(CollaboratorRepository collaboratorRepo,
                                WorkTaskRepository workTaskRepo,
                                AssetInfoRepository assetInfoRepo,
                                UserRepository userRepo) {
        this.collaboratorRepo = collaboratorRepo;
        this.workTaskRepo     = workTaskRepo;
        this.assetInfoRepo    = assetInfoRepo;
        this.userRepo         = userRepo;
    }

    /**
     * Invites one or more users to collaborate on a task.
     * Only the worker to whom the task is assigned may add collaborators.
     */
    @Transactional
    public List<TaskCollaborator> addCollaborators(String taskId, int callerUserId,
                                                   List<Integer> userIds) {
        WorkTask task = workTaskRepo.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        if (task.getAssignedTo() == null || task.getAssignedTo() != callerUserId) {
            throw new BadRequestException("Only the assigned worker can invite collaborators.");
        }
        if (userIds == null || userIds.isEmpty()) {
            throw new BadRequestException("At least one user must be selected.");
        }
        // Prevent the worker from adding themselves
        List<Integer> filtered = userIds.stream()
                .filter(uid -> uid != callerUserId)
                .collect(Collectors.toList());
        if (filtered.isEmpty()) {
            throw new BadRequestException("Cannot add yourself as a collaborator.");
        }
        collaboratorRepo.addCollaborators(taskId, filtered);
        return collaboratorRepo.findByTaskId(taskId);
    }

    /** Returns all collaborators on a task. Caller must be the worker or a collaborator. */
    public List<TaskCollaborator> getCollaborators(String taskId, int callerUserId) {
        assertAccess(taskId, callerUserId);
        return collaboratorRepo.findByTaskId(taskId);
    }

    /**
     * Returns all collaboration tasks for this user — as owner, requestor,
     * collaborator, or admin. Ordered newest-first.
     */
    public List<WorkTaskResponse> getMyCollaborations(int userId) {
        Map<String, WorkTaskResponse> merged = new LinkedHashMap<>();

        // Tasks the user owns (assigned worker who has at least one collaborator)
        for (WorkTask t : collaboratorRepo.findOwnedTasksWithCollaborators(userId)) {
            WorkTaskResponse r = CampaignService.toWorkTaskResponse(t);
            r.setMyRole("OWNER");
            merged.put(t.getTaskId(), r);
        }

        // Tasks the user is a collaborator on (invited or auto-added as requestor)
        for (WorkTask t : collaboratorRepo.findTasksByCollaboratorUserId(userId)) {
            if (!merged.containsKey(t.getTaskId())) {
                WorkTaskResponse r = CampaignService.toWorkTaskResponse(t);
                // If this user is the campaign requestor, show "REQUESTOR" role
                String role = t.getRequestorId() != null
                        && t.getRequestorId().equals(userId) ? "REQUESTOR" : "COLLABORATOR";
                r.setMyRole(role);
                merged.put(t.getTaskId(), r);
            }
        }

        // Resolve the caller's roles once for all subsequent checks
        User caller = userRepo.findById((long) userId).orElse(null);
        boolean isAdmin = caller != null
                && caller.getRoleIds() != null
                && caller.getRoleIds().contains("1");
        boolean isMarketingManager = caller != null
                && caller.getRoleNames() != null
                && caller.getRoleNames().stream().anyMatch(n -> n.equalsIgnoreCase("Marketing Manager"));
        boolean isRequestor = caller != null
                && caller.getRoleNames() != null
                && caller.getRoleNames().stream().anyMatch(n -> n.equalsIgnoreCase("Requestor"));

        // Requestors (and MMs who may also be requestors) see collaborations on
        // campaigns they created — but ONLY if they have the Requestor role.
        // This prevents workers from accidentally seeing requestor-level cards.
        if (isRequestor || isMarketingManager || isAdmin) {
            for (WorkTask t : collaboratorRepo.findTasksByRequestorId(userId)) {
                if (!merged.containsKey(t.getTaskId())) {
                    WorkTaskResponse r = CampaignService.toWorkTaskResponse(t);
                    r.setMyRole("REQUESTOR");
                    merged.put(t.getTaskId(), r);
                }
            }
        }

        // Admins and Marketing Managers see every task that has at least one
        // collaborator (i.e., the worker has clicked "Collaborate").
        if (isAdmin || isMarketingManager) {
            String role = isAdmin ? "ADMIN" : "MANAGER";
            for (WorkTask t : collaboratorRepo.findAllTasksWithAnyCollaborator()) {
                if (!merged.containsKey(t.getTaskId())) {
                    WorkTaskResponse r = CampaignService.toWorkTaskResponse(t);
                    r.setMyRole(role);
                    merged.put(t.getTaskId(), r);
                }
            }
        }

        // Only expose tasks where the worker has explicitly clicked "Collaborate".
        // This guards against stale collaborator rows where is_collaboration_started
        // was never set, regardless of which query path added the task.
        return merged.values().stream()
                .filter(WorkTaskResponse::isCollaborationStarted)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Starts collaboration on a task: marks is_collaboration_started = true,
     * auto-adds the worker and requestor as collaborators, and — if the task is
     * already IN_PROGRESS — also marks it active immediately.
     *
     * Blocked when task is HELD, QC_REVIEW, or COMPLETED (business rule 6).
     * Can be called only by the task's assigned worker.
     */
    @Transactional
    public void startCollaboration(String taskId, int callerUserId) {
        WorkTask task = workTaskRepo.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        if (task.getAssignedTo() == null || task.getAssignedTo() != callerUserId) {
            throw new BadRequestException("Only the assigned worker can start collaboration.");
        }
        // Rule 6: block collaborate when task cannot be worked on
        if (task.getStatus() == com.medplus.marketing_automation_backend.enums.TaskStatus.HELD
                || task.getStatus() == com.medplus.marketing_automation_backend.enums.TaskStatus.QC_REVIEW
                || task.getStatus() == com.medplus.marketing_automation_backend.enums.TaskStatus.COMPLETED) {
            throw new BadRequestException(
                    "Collaboration cannot be started when the task is " + task.getStatus() + ".");
        }
        // Mark started (and activate immediately when task is already in progress)
        boolean isInProgress = task.getStatus() == com.medplus.marketing_automation_backend.enums.TaskStatus.IN_PROGRESS;
        workTaskRepo.markCollaborationStarted(taskId, isInProgress);
        // Always add the worker so the card appears in their OWNER view.
        collaboratorRepo.addSingleCollaborator(taskId, callerUserId);
        // Auto-add requestor so they can join without an explicit invite.
        if (task.getRequestorId() != null && task.getRequestorId() > 0) {
            collaboratorRepo.addSingleCollaborator(taskId, task.getRequestorId());
        }
    }

    /**
     * Toggles the collaboration active flag (Pause Chat / Resume Chat).
     * When paused (active = false): chat is blocked; assets remain uploadable.
     * Can be called by the task's assigned worker or an admin.
     * Only permitted when the task is in an open-work status (not held/QC/completed).
     */
    @Transactional
    public void pauseCollaboration(String taskId, int callerUserId) {
        WorkTask task = workTaskRepo.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        User caller = userRepo.findById((long) callerUserId).orElse(null);
        boolean isAdmin = caller != null
                && caller.getRoleIds() != null
                && caller.getRoleIds().contains("1");
        boolean isWorker = task.getAssignedTo() != null && task.getAssignedTo() == callerUserId;
        if (!isWorker && !isAdmin) {
            throw new BadRequestException("Only the assigned worker or an admin can pause/resume collaboration chat.");
        }
        if (!task.isCollaborationStarted()) {
            throw new BadRequestException("Collaboration has not been started on this task.");
        }
        // Toggle: if currently active → deactivate; if currently inactive → activate
        if (task.isCollaborationActive()) {
            workTaskRepo.deactivateCollaboration(taskId);
        } else {
            workTaskRepo.activateCollaboration(taskId);
        }
    }

    /** Returns all active users — used to populate the collaborator picker on the frontend. */
    public List<User> getAllUsers() {
        return userRepo.findAll(false);
    }

    // ── Assets ────────────────────────────────────────────────────────────────

    /** Add an asset to a task with its thumbnail and original filename. Caller must have access. */
    public AssetInfo addAsset(String taskId, int callerUserId,
                              String url, String thumbnailUrl, String originalFilename) {
        assertAccess(taskId, callerUserId);
        if (url == null || url.isBlank()) {
            throw new BadRequestException("Asset URL must not be blank.");
        }
        assetInfoRepo.insert(taskId, callerUserId, url.trim(), thumbnailUrl, originalFilename);
        return assetInfoRepo.findByTaskId(taskId).stream()
                .filter(a -> a.getUrl().equals(url.trim()))
                .reduce((first, second) -> second) // last inserted
                .orElseThrow();
    }

    /** Delete an asset. Only the uploader can delete their own asset. */
    public void deleteAsset(int assetId, int callerUserId) {
        int deleted = assetInfoRepo.deleteByIdAndUserId(assetId, callerUserId);
        if (deleted == 0) {
            throw new BadRequestException("Asset not found or you are not the uploader.");
        }
    }

    /** List all assets for a task. Caller must have access. */
    public List<AssetInfo> getAssets(String taskId, int callerUserId) {
        assertAccess(taskId, callerUserId);
        return assetInfoRepo.findByTaskId(taskId);
    }

    /**
     * Proxy-download an asset: fetches the remote file bytes and returns them
     * so the browser receives the file from the same origin (avoids cross-origin
     * download restriction).
     * Returns a pair of [bytes, contentType].
     */
    /** Holds both the raw bytes and the actual Content-Type header from the CDN. */
    public record DownloadResult(byte[] bytes, String contentType) {}

    /**
     * Downloads an asset from the external CDN using Java's built-in HttpClient.
     * Uses trust-all SSL, follows redirects, and sends a browser-like User-Agent
     * so the CDN returns the actual file rather than an HTML error/index page.
     */
    public DownloadResult downloadAsset(int assetId) {
        AssetInfo asset = assetInfoRepo.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found: " + assetId));

        String rawUrl = asset.getUrl();
        // Normalise accidental double slashes in the path (e.g. "https://host:port//LT/…")
        String url = rawUrl.replaceAll("(?<!:)//+", "/");

        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{ new X509TrustManager() {
                @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                @Override public void checkClientTrusted(X509Certificate[] c, String a) {}
                @Override public void checkServerTrusted(X509Certificate[] c, String a) {}
            }}, new SecureRandom());

            HttpClient client = HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(120))
                    // Mimic a browser so CDN servers return the actual file
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept", "*/*")
                    .GET()
                    .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() >= 400) {
                throw new RuntimeException("CDN returned HTTP " + response.statusCode() + " for asset " + assetId);
            }

            String contentType = response.headers().firstValue("content-type").orElse(null);

            // Detect HTML error pages — the CDN sometimes returns HTML for invalid/expired URLs
            byte[] body = response.body() != null ? response.body() : new byte[0];
            if (body.length > 0 && contentType != null && contentType.startsWith("text/html")) {
                throw new RuntimeException(
                        "CDN returned an HTML page instead of the file. The asset URL may have expired.");
            }

            return new DownloadResult(body, contentType);

        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("Failed to download asset " + assetId + ": " + e.getMessage(), e);
        }
    }

    /** Returns just the AssetInfo (for filename / content-type detection). */
    public AssetInfo getAssetInfo(int assetId) {
        return assetInfoRepo.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found: " + assetId));
    }

    // ── Access guard ──────────────────────────────────────────────────────────

    /**
     * Asserts that the caller is the task's assigned worker, a collaborator,
     * or an admin (role_id "1"). Admins bypass all task-level restrictions.
     */
    public void assertAccess(String taskId, int callerUserId) {
        // Admin bypass
        User caller = userRepo.findById((long) callerUserId).orElse(null);
        if (caller != null && caller.getRoleIds() != null && caller.getRoleIds().contains("1")) {
            return;
        }
        WorkTask task = workTaskRepo.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        boolean isWorker       = task.getAssignedTo() != null && task.getAssignedTo() == callerUserId;
        boolean isCollaborator = collaboratorRepo.isCollaborator(taskId, callerUserId);
        if (!isWorker && !isCollaborator) {
            throw new BadRequestException("Access denied — you are not a participant on this task.");
        }
    }
}

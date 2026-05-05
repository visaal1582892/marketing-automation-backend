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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

        // Admins and Marketing Managers see every task with collaborators
        User caller = userRepo.findById((long) userId).orElse(null);
        boolean isAdmin = caller != null
                && caller.getRoleIds() != null
                && caller.getRoleIds().contains("1");
        boolean isMarketingManager = caller != null
                && caller.getRoleNames() != null
                && caller.getRoleNames().stream().anyMatch(n -> n.equalsIgnoreCase("Marketing Manager"));

        if (isAdmin) {
            for (WorkTask t : collaboratorRepo.findAllTasksWithOpenChat()) {
                if (!merged.containsKey(t.getTaskId())) {
                    WorkTaskResponse r = CampaignService.toWorkTaskResponse(t);
                    r.setMyRole("ADMIN");
                    merged.put(t.getTaskId(), r);
                }
            }
        } else if (isMarketingManager) {
            for (WorkTask t : collaboratorRepo.findAllTasksWithAnyCollaborator()) {
                if (!merged.containsKey(t.getTaskId())) {
                    WorkTaskResponse r = CampaignService.toWorkTaskResponse(t);
                    r.setMyRole("MANAGER");
                    merged.put(t.getTaskId(), r);
                }
            }
        }

        return new ArrayList<>(merged.values());
    }

    /**
     * Starts collaboration on a task: ensures the requestor is a collaborator and
     * holds the task so the worker can focus on the collaboration chat.
     * Can be called by the task's assigned worker.
     */
    @Transactional
    public void startCollaboration(String taskId, int callerUserId) {
        WorkTask task = workTaskRepo.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        if (task.getAssignedTo() == null || task.getAssignedTo() != callerUserId) {
            throw new BadRequestException("Only the assigned worker can start collaboration.");
        }
        // Ensure requestor is a collaborator (auto-add if not already)
        if (task.getRequestorId() != null && task.getRequestorId() > 0) {
            collaboratorRepo.addSingleCollaborator(taskId, task.getRequestorId());
        }
        // Hold the task
        workTaskRepo.holdForCollaboration(taskId);
    }

    /**
     * Pauses collaboration: restores the task to its pre-hold status so it
     * re-enters the active work queue at the top.
     * Can be called by the task's assigned worker.
     */
    @Transactional
    public void pauseCollaboration(String taskId, int callerUserId) {
        WorkTask task = workTaskRepo.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        // Allow the worker or an admin to pause
        User caller = userRepo.findById((long) callerUserId).orElse(null);
        boolean isAdmin = caller != null
                && caller.getRoleIds() != null
                && caller.getRoleIds().contains("1");
        boolean isWorker = task.getAssignedTo() != null && task.getAssignedTo() == callerUserId;
        if (!isWorker && !isAdmin) {
            throw new BadRequestException("Only the assigned worker or an admin can pause collaboration.");
        }
        workTaskRepo.resumeFromCollaboration(taskId);
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

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
     * Returns all collaboration tasks for this user — either as the task owner
     * (assigned worker who has at least one collaborator) or as an invited
     * collaborator. Ordered newest-first. Owner tasks appear first when both
     * roles apply to the same task.
     */
    public List<WorkTaskResponse> getMyCollaborations(int userId) {
        Map<String, WorkTaskResponse> merged = new LinkedHashMap<>();

        // Tasks the user owns that have at least one collaborator
        for (WorkTask t : collaboratorRepo.findOwnedTasksWithCollaborators(userId)) {
            WorkTaskResponse r = CampaignService.toWorkTaskResponse(t);
            r.setMyRole("OWNER");
            merged.put(t.getTaskId(), r);
        }

        // Tasks the user was invited to collaborate on
        for (WorkTask t : collaboratorRepo.findTasksByCollaboratorUserId(userId)) {
            if (!merged.containsKey(t.getTaskId())) {
                WorkTaskResponse r = CampaignService.toWorkTaskResponse(t);
                r.setMyRole("COLLABORATOR");
                merged.put(t.getTaskId(), r);
            }
        }

        return new ArrayList<>(merged.values());
    }

    /** Returns all active users — used to populate the collaborator picker on the frontend. */
    public List<User> getAllUsers() {
        return userRepo.findAll(false);
    }

    // ── Assets ────────────────────────────────────────────────────────────────

    /** Add an asset URL to a task. Caller must be the worker or a collaborator. */
    public AssetInfo addAsset(String taskId, int callerUserId, String url) {
        assertAccess(taskId, callerUserId);
        if (url == null || url.isBlank()) {
            throw new BadRequestException("Asset URL must not be blank.");
        }
        assetInfoRepo.insert(taskId, callerUserId, url.trim());
        return assetInfoRepo.findByTaskId(taskId).stream()
                .filter(a -> a.getUrl().equals(url.trim()))
                .reduce((first, second) -> second) // last inserted
                .orElseThrow();
    }

    /** List all assets for a task. Caller must be the worker or a collaborator. */
    public List<AssetInfo> getAssets(String taskId, int callerUserId) {
        assertAccess(taskId, callerUserId);
        return assetInfoRepo.findByTaskId(taskId);
    }

    // ── Access guard ──────────────────────────────────────────────────────────

    /**
     * Asserts that the caller is either the task's assigned worker or a collaborator.
     */
    public void assertAccess(String taskId, int callerUserId) {
        WorkTask task = workTaskRepo.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        boolean isWorker       = task.getAssignedTo() != null && task.getAssignedTo() == callerUserId;
        boolean isCollaborator = collaboratorRepo.isCollaborator(taskId, callerUserId);
        if (!isWorker && !isCollaborator) {
            throw new BadRequestException("Access denied — you are not a participant on this task.");
        }
    }
}

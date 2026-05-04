package com.medplus.marketing_automation_backend.service;

import com.medplus.marketing_automation_backend.domain.TaskMessage;
import com.medplus.marketing_automation_backend.domain.WorkTask;
import com.medplus.marketing_automation_backend.enums.TaskStatus;
import com.medplus.marketing_automation_backend.exception.BadRequestException;
import com.medplus.marketing_automation_backend.repository.TaskMessageRepository;
import com.medplus.marketing_automation_backend.repository.WorkTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TaskMessageService {

    private final TaskMessageRepository messageRepo;
    private final CollaborationService  collaborationService;
    private final WorkTaskRepository    workTaskRepo;

    public TaskMessageService(TaskMessageRepository messageRepo,
                              CollaborationService collaborationService,
                              WorkTaskRepository workTaskRepo) {
        this.messageRepo          = messageRepo;
        this.collaborationService = collaborationService;
        this.workTaskRepo         = workTaskRepo;
    }

    /**
     * Persists a new chat message.
     * Blocks if the task is COMPLETED — chat becomes read-only at that point.
     */
    @Transactional
    public TaskMessage sendMessage(String taskId, int userId, String text) {
        collaborationService.assertAccess(taskId, userId);

        WorkTask task = workTaskRepo.findById(taskId).orElseThrow();
        if (task.getStatus() == TaskStatus.COMPLETED) {
            throw new BadRequestException("Chat is read-only — the task is COMPLETED.");
        }
        if (text == null || text.isBlank()) {
            throw new BadRequestException("Message must not be blank.");
        }
        return messageRepo.insert(taskId, userId, text.trim());
    }

    /**
     * Returns all messages for a task.
     * Caller must be the assigned worker or a collaborator.
     * COMPLETED tasks are still visible (read-only).
     */
    public List<TaskMessage> getMessages(String taskId, int userId) {
        collaborationService.assertAccess(taskId, userId);
        return messageRepo.findByTaskId(taskId);
    }
}

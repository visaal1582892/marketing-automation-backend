package com.medplus.marketing_automation_backend.service;

import com.medplus.marketing_automation_backend.domain.GranularTask;
import com.medplus.marketing_automation_backend.exception.BadRequestException;
import com.medplus.marketing_automation_backend.exception.ResourceNotFoundException;
import com.medplus.marketing_automation_backend.repository.GranularTaskRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GranularTaskService {

    private final GranularTaskRepository repo;

    public GranularTaskService(GranularTaskRepository repo) {
        this.repo = repo;
    }

    public List<GranularTask> list(boolean includeInactive) {
        return repo.findAll(includeInactive);
    }

    public List<GranularTask> listByTaskType(String taskTypeId) {
        return repo.findByTaskType(taskTypeId);
    }

    public GranularTask get(String id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Granular task " + id + " not found"));
    }

    public GranularTask create(GranularTask task) {
        validate(task);
        if (repo.existsByName(task.getTaskName().trim(), null)) {
            throw new BadRequestException("'" + task.getTaskName() + "' already exists");
        }
        task.setTaskName(task.getTaskName().trim());
        String id = repo.insert(task);
        return repo.findById(id).orElseThrow();
    }

    public GranularTask update(String id, GranularTask task) {
        repo.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Granular task " + id + " not found"));
        validate(task);
        if (repo.existsByName(task.getTaskName().trim(), id)) {
            throw new BadRequestException("'" + task.getTaskName() + "' already exists");
        }
        task.setTaskName(task.getTaskName().trim());
        repo.update(id, task);
        return repo.findById(id).orElseThrow();
    }

    /** Permanently removes the granular task from the database. */
    public void delete(String id) {
        if (repo.delete(id) == 0) {
            throw new ResourceNotFoundException("Granular task " + id + " not found");
        }
    }

    private void validate(GranularTask task) {
        if (task == null || task.getTaskName() == null || task.getTaskName().isBlank()) {
            throw new BadRequestException("taskName is required");
        }
        if (task.getTaskTypeId() == null || task.getTaskTypeId().isBlank()) {
            throw new BadRequestException("taskTypeId is required");
        }
    }
}

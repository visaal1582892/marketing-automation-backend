package com.medplus.marketing_automation_backend.controller;

import com.medplus.marketing_automation_backend.domain.DynamicQuestion;
import com.medplus.marketing_automation_backend.domain.GranularTask;
import com.medplus.marketing_automation_backend.service.GranularTaskService;
import com.medplus.marketing_automation_backend.service.QuestionnaireService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin CRUD for granular tasks (the fine-grained deliverables on the Smart Form).
 *
 * <pre>
 *   GET    /api/master/granular-tasks                          list active tasks
 *   GET    /api/master/granular-tasks?includeInactive=true     list all
 *   GET    /api/master/granular-tasks?taskTypeId=TASK-TYPE-1   filter by type
 *   GET    /api/master/granular-tasks/{id}                     fetch one
 *   POST   /api/master/granular-tasks                          create
 *   PUT    /api/master/granular-tasks/{id}                     update
 *   DELETE /api/master/granular-tasks/{id}                     hard-delete
 * </pre>
 */
@RestController
@RequestMapping("/api/master/granular-tasks")
public class GranularTaskController {

    private final GranularTaskService    service;
    private final QuestionnaireService   questionnaireService;

    public GranularTaskController(GranularTaskService service,
                                  QuestionnaireService questionnaireService) {
        this.service                = service;
        this.questionnaireService   = questionnaireService;
    }

    /**
     * List granular tasks.
     * Without page/size: returns full list (used by Smart Form dropdowns).
     * With page/size: returns PagedResponse for the admin Granular Tasks table.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Object list(
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive,
            @RequestParam(required = false) String taskTypeId,
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) String taskName,
            @RequestParam(required = false) String taskTypeName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        if (page != null || size != null) {
            String effectiveStatus = (status != null && !status.isBlank()) ? status
                    : (includeInactive ? "all" : "ACTIVE");
            return service.listPaged(taskId, taskName, taskTypeName, effectiveStatus,
                                     page != null ? page : 0,
                                     size != null ? size : 20);
        }
        if (taskTypeId != null && !taskTypeId.isBlank()) {
            return service.listByTaskType(taskTypeId);
        }
        return service.list(includeInactive);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public GranularTask get(@PathVariable String id) {
        return service.get(id);
    }

    /**
     * Dynamic questions mapped to this granular task (for the new-request form).
     * Empty list when none are configured.
     */
    @GetMapping("/{id}/questions")
    @PreAuthorize("isAuthenticated()")
    public List<DynamicQuestion> getQuestionsForGranularTask(@PathVariable String id) {
        return questionnaireService.getQuestionsForGranularTask(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MARKETING_MANAGER')")
    public ResponseEntity<GranularTask> create(@Valid @RequestBody GranularTask task) {
        return ResponseEntity.status(201).body(service.create(task));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MARKETING_MANAGER')")
    public GranularTask update(@PathVariable String id, @Valid @RequestBody GranularTask task) {
        return service.update(id, task);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MARKETING_MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

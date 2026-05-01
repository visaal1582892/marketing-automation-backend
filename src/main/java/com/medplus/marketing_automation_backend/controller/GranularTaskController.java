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

    /** Any authenticated user can read tasks — needed by the Smart Form. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<GranularTask> list(
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive,
            @RequestParam(required = false) String taskTypeId) {
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

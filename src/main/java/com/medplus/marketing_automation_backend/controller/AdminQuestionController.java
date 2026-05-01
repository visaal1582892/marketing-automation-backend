package com.medplus.marketing_automation_backend.controller;

import com.medplus.marketing_automation_backend.domain.DynamicQuestion;
import com.medplus.marketing_automation_backend.dto.QuestionResponse;
import com.medplus.marketing_automation_backend.dto.QuestionUpsertRequest;
import com.medplus.marketing_automation_backend.enums.FieldType;
import com.medplus.marketing_automation_backend.exception.BadRequestException;
import com.medplus.marketing_automation_backend.exception.ResourceNotFoundException;
import com.medplus.marketing_automation_backend.repository.DynamicQuestionRepository;
import com.medplus.marketing_automation_backend.repository.TaskQuestionMappingRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Admin CRUD for the dynamic question library and task→question mappings.
 */
@RestController
@RequestMapping("/api/admin/questions")
@PreAuthorize("hasAnyRole('ADMIN','MARKETING_MANAGER')")
public class AdminQuestionController {

    private final DynamicQuestionRepository     questionRepo;
    private final TaskQuestionMappingRepository mappingRepo;

    public AdminQuestionController(DynamicQuestionRepository     questionRepo,
                                   TaskQuestionMappingRepository mappingRepo) {
        this.questionRepo = questionRepo;
        this.mappingRepo  = mappingRepo;
    }

    @GetMapping
    public List<QuestionResponse> listAll() {
        List<Map<String, Object>> rows = questionRepo.findAllWithMappings();
        List<QuestionResponse> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            result.add(toResponse(row));
        }
        return result;
    }

    @PostMapping
    @Transactional
    public QuestionResponse create(@RequestBody QuestionUpsertRequest req) {
        validate(req);
        DynamicQuestion q = DynamicQuestion.builder()
                .questionText(req.getQuestionText().trim())
                .fieldType(FieldType.valueOf(req.getFieldType().trim().toUpperCase()))
                .options(sanitizeOptions(req))
                .isRequired(req.getIsRequired() == null || req.getIsRequired())
                .build();
        questionRepo.save(q);

        if (req.getGranularTaskIds() != null) {
            for (String taskId : req.getGranularTaskIds()) {
                if (taskId != null && !taskId.isBlank()) {
                    mappingRepo.map(taskId.trim(), q.getQuestionId());
                }
            }
        }

        return getAsResponse(q.getQuestionId());
    }

    @PutMapping("/{questionId}")
    @Transactional
    public QuestionResponse update(@PathVariable String questionId,
                                   @RequestBody QuestionUpsertRequest req) {
        questionRepo.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found: " + questionId));
        validate(req);

        DynamicQuestion q = DynamicQuestion.builder()
                .questionId(questionId)
                .questionText(req.getQuestionText().trim())
                .fieldType(FieldType.valueOf(req.getFieldType().trim().toUpperCase()))
                .options(sanitizeOptions(req))
                .isRequired(req.getIsRequired() == null || req.getIsRequired())
                .build();
        questionRepo.update(q);

        if (req.getGranularTaskIds() != null) {
            mappingRepo.deleteAllForQuestion(questionId);
            for (String taskId : req.getGranularTaskIds()) {
                if (taskId != null && !taskId.isBlank()) {
                    mappingRepo.map(taskId.trim(), questionId);
                }
            }
        }

        return getAsResponse(questionId);
    }

    @DeleteMapping("/{questionId}")
    public ResponseEntity<Void> delete(@PathVariable String questionId) {
        int deleted = questionRepo.delete(questionId);
        if (deleted == 0) {
            throw new ResourceNotFoundException("Question not found: " + questionId);
        }
        return ResponseEntity.noContent().build();
    }

    private QuestionResponse getAsResponse(String questionId) {
        List<Map<String, Object>> all = questionRepo.findAllWithMappings();
        return all.stream()
                .filter(r -> questionId.equals(String.valueOf(r.get("question_id"))))
                .map(this::toResponse)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Question not found: " + questionId));
    }

    private QuestionResponse toResponse(Map<String, Object> row) {
        String taskIdsRaw   = (String) row.get("task_ids");
        String taskNamesRaw = (String) row.get("task_names");

        List<QuestionResponse.MappedTask> tasks = new ArrayList<>();
        if (taskIdsRaw != null && !taskIdsRaw.isBlank()) {
            String[] ids   = taskIdsRaw.split(",");
            String[] names = taskNamesRaw != null ? taskNamesRaw.split("\\|\\|") : new String[0];
            for (int i = 0; i < ids.length; i++) {
                tasks.add(QuestionResponse.MappedTask.builder()
                        .granularTaskId(ids[i].trim())
                        .granularTaskName(i < names.length ? names[i] : ids[i].trim())
                        .build());
            }
        }

        Object isReqRaw = row.get("is_required");
        boolean required = isReqRaw instanceof Number n ? n.intValue() != 0 : Boolean.TRUE.equals(isReqRaw);

        return QuestionResponse.builder()
                .questionId((String) row.get("question_id"))
                .questionText((String) row.get("question_text"))
                .fieldType((String) row.get("field_type"))
                .options((String) row.get("options"))
                .required(required)
                .mappedTasks(tasks)
                .build();
    }

    private void validate(QuestionUpsertRequest req) {
        if (req.getQuestionText() == null || req.getQuestionText().isBlank()) {
            throw new BadRequestException("questionText is required");
        }
        if (req.getFieldType() == null || req.getFieldType().isBlank()) {
            throw new BadRequestException("fieldType is required");
        }
        try {
            FieldType.valueOf(req.getFieldType().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("fieldType must be one of: TEXT, NUMBER, TEXTAREA, DROPDOWN, MULTISELECT, DATE");
        }
    }

    private String sanitizeOptions(QuestionUpsertRequest req) {
        String type = req.getFieldType().trim().toUpperCase();
        if ("DROPDOWN".equals(type) || "MULTISELECT".equals(type)) {
            return (req.getOptions() != null && !req.getOptions().isBlank())
                    ? req.getOptions().trim()
                    : null;
        }
        return null;
    }
}

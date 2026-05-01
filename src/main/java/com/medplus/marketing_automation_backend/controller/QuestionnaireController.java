package com.medplus.marketing_automation_backend.controller;

import com.medplus.marketing_automation_backend.domain.DynamicQuestion;
import com.medplus.marketing_automation_backend.domain.WorkTaskAnswer;
import com.medplus.marketing_automation_backend.dto.WorkTaskAnswerRequest;
import com.medplus.marketing_automation_backend.service.QuestionnaireService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for the task-specific dynamic questionnaire feature.
 *
 * <pre>
 * GET  /api/tasks/{taskId}/questions   — fetch questions for a task
 * POST /api/tasks/{taskId}/answers     — batch-save worker answers
 * GET  /api/tasks/{taskId}/answers     — retrieve previously saved answers
 * </pre>
 *
 * These routes are intentionally placed under /api/tasks (same prefix as
 * WorkTaskController) so that the task ID is the natural primary resource.
 */
@RestController
@RequestMapping("/api/tasks/{taskId}")
public class QuestionnaireController {

    private final QuestionnaireService questionnaireService;

    public QuestionnaireController(QuestionnaireService questionnaireService) {
        this.questionnaireService = questionnaireService;
    }

    /**
     * Returns the dynamic questions that apply to a specific work task.
     * The question set is determined by the task's granular task type,
     * via the {@code task_question_mapping} table.
     *
     * <p>Returns an empty list if no questions are mapped to this task type —
     * not a 404, so the client can cleanly skip rendering the questionnaire
     * section without special-casing the error.
     *
     * @param taskId WORK-TASK-X identifier
     */
    @GetMapping("/questions")
    public List<DynamicQuestion> getQuestions(@PathVariable String taskId) {
        return questionnaireService.getQuestionsForTask(taskId);
    }

    /**
     * Batch-saves (or updates) a worker's answers for a work task.
     *
     * <p>Example request body:
     * <pre>
     * {
     *   "answers": [
     *     { "questionId": "QUES-1", "answerValue": "Facebook" },
     *     { "questionId": "QUES-2", "answerValue": "Promote summer discounts" }
     *   ]
     * }
     * </pre>
     *
     * @param taskId WORK-TASK-X identifier
     * @param req    batch answer payload
     */
    @PostMapping("/answers")
    public ResponseEntity<Void> submitAnswers(@PathVariable String taskId,
                                              @RequestBody WorkTaskAnswerRequest req) {
        questionnaireService.saveAnswers(taskId, req.getAnswers());
        return ResponseEntity.ok().build();
    }

    /**
     * Returns all answers previously submitted for this task.
     * Useful for pre-filling the questionnaire form when the worker
     * revisits the task.
     *
     * @param taskId WORK-TASK-X identifier
     */
    @GetMapping("/answers")
    public List<WorkTaskAnswer> getAnswers(@PathVariable String taskId) {
        return questionnaireService.getAnswers(taskId);
    }
}

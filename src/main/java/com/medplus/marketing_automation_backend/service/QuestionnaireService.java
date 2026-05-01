package com.medplus.marketing_automation_backend.service;

import com.medplus.marketing_automation_backend.domain.DynamicQuestion;
import com.medplus.marketing_automation_backend.domain.WorkTask;
import com.medplus.marketing_automation_backend.domain.WorkTaskAnswer;
import com.medplus.marketing_automation_backend.enums.TaskStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medplus.marketing_automation_backend.dto.WorkTaskAnswerBriefRow;
import com.medplus.marketing_automation_backend.dto.WorkTaskAnswerRequest;
import com.medplus.marketing_automation_backend.dto.WorkTaskQuestionnaireBriefItem;
import com.medplus.marketing_automation_backend.exception.ResourceNotFoundException;
import com.medplus.marketing_automation_backend.repository.DynamicQuestionRepository;
import com.medplus.marketing_automation_backend.repository.GranularTaskRepository;
import com.medplus.marketing_automation_backend.repository.TaskQuestionMappingRepository;
import com.medplus.marketing_automation_backend.repository.WorkTaskAnswerRepository;
import com.medplus.marketing_automation_backend.repository.WorkTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Business logic for the dynamic questionnaire feature.
 *
 * <ul>
 *   <li>Fetch questions that are mapped to a specific work task.
 *   <li>Batch-save a worker's answers for a work task.
 *   <li>Retrieve previously saved answers for a work task.
 * </ul>
 */
@Slf4j
@Service
public class QuestionnaireService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final DynamicQuestionRepository       questionRepo;
    private final WorkTaskAnswerRepository        answerRepo;
    private final WorkTaskRepository              workTaskRepo;
    private final GranularTaskRepository          granularTaskRepo;
    private final TaskQuestionMappingRepository   taskQuestionMappingRepo;

    public QuestionnaireService(DynamicQuestionRepository questionRepo,
                                WorkTaskAnswerRepository  answerRepo,
                                WorkTaskRepository        workTaskRepo,
                                GranularTaskRepository    granularTaskRepo,
                                TaskQuestionMappingRepository taskQuestionMappingRepo) {
        this.questionRepo           = questionRepo;
        this.answerRepo             = answerRepo;
        this.workTaskRepo           = workTaskRepo;
        this.granularTaskRepo       = granularTaskRepo;
        this.taskQuestionMappingRepo = taskQuestionMappingRepo;
    }

    // -------------------------------------------------------------------------
    // Questions
    // -------------------------------------------------------------------------

    /**
     * Returns all dynamic questions mapped to the given work task.
     * The mapping is determined by the task's {@code granular_task_id}.
     *
     * @param workTaskId the WORK-TASK-X string identifier
     */
    public List<DynamicQuestion> getQuestionsForTask(String workTaskId) {
        workTaskRepo.findById(workTaskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + workTaskId));
        return questionRepo.findByWorkTaskId(workTaskId);
    }

    /**
     * Questions for a granular task on the new-request form (no work task yet).
     */
    public List<DynamicQuestion> getQuestionsForGranularTask(String granularTaskId) {
        granularTaskRepo.findById(granularTaskId)
                .orElseThrow(() -> new ResourceNotFoundException("Granular task not found: " + granularTaskId));
        return questionRepo.findByGranularTaskId(granularTaskId);
    }

    /**
     * Saves requestor-supplied answers when routing creates a work task. Only question IDs
     * mapped to the granular task are accepted.
     */
    @Transactional
    public void savePrefilledAnswers(String workTaskId, String granularTaskId,
                                     List<WorkTaskAnswerRequest.AnswerItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        workTaskRepo.findById(workTaskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + workTaskId));
        Set<String> allowed = new HashSet<>(taskQuestionMappingRepo.findQuestionIdsByGranularTaskId(granularTaskId));
        for (WorkTaskAnswerRequest.AnswerItem item : items) {
            if (item == null || item.getQuestionId() == null || item.getQuestionId().isBlank()) {
                continue;
            }
            if (!allowed.contains(item.getQuestionId())) {
                continue;
            }
            WorkTaskAnswer answer = WorkTaskAnswer.builder()
                    .workTaskId(workTaskId)
                    .questionId(item.getQuestionId())
                    .answerValue(item.getAnswerValue())
                    .build();
            answerRepo.save(answer);
        }
    }

    /**
     * Questionnaire lines grouped by work task for the campaign detail / request brief APIs.
     */
    public Map<String, List<WorkTaskQuestionnaireBriefItem>> questionnaireBriefByWorkTaskForCampaign(
            int campaignId) {
        List<WorkTaskAnswerBriefRow> rows = answerRepo.findQuestionnaireBriefForCampaign(campaignId);
        Map<String, List<WorkTaskQuestionnaireBriefItem>> out = new LinkedHashMap<>();
        for (WorkTaskAnswerBriefRow row : rows) {
            WorkTaskQuestionnaireBriefItem item = WorkTaskQuestionnaireBriefItem.builder()
                    .questionId(row.questionId())
                    .questionText(row.questionText())
                    .fieldType(row.fieldType())
                    .answerDisplay(formatBriefAnswer(row.fieldType(), row.answerValue()))
                    .build();
            out.computeIfAbsent(row.workTaskId(), k -> new ArrayList<>()).add(item);
        }
        return out;
    }

    private static String formatBriefAnswer(String fieldType, String raw) {
        if (raw == null || raw.isBlank()) {
            return "—";
        }
        if ("MULTISELECT".equals(fieldType)) {
            try {
                List<String> list = JSON.readValue(raw, new TypeReference<List<String>>() { });
                if (list == null || list.isEmpty()) {
                    return "—";
                }
                return String.join(", ", list);
            } catch (Exception e) {
                return raw;
            }
        }
        return raw;
    }

    // -------------------------------------------------------------------------
    // Answers
    // -------------------------------------------------------------------------

    /**
     * Batch-saves a worker's answers for a work task. Uses upsert semantics —
     * re-submitting an answer for the same question replaces the previous value
     * so the worker can correct mistakes before final submission.
     *
     * @param workTaskId  the WORK-TASK-X string identifier
     * @param answerItems list of question-answer pairs from the request body
     */
    @Transactional
    public void saveAnswers(String workTaskId, List<WorkTaskAnswerRequest.AnswerItem> answerItems) {
        WorkTask task = workTaskRepo.findById(workTaskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + workTaskId));

        if (answerItems == null || answerItems.isEmpty()) return;

        for (WorkTaskAnswerRequest.AnswerItem item : answerItems) {
            if (item.getQuestionId() == null || item.getQuestionId().isBlank()) continue;
            WorkTaskAnswer answer = WorkTaskAnswer.builder()
                    .workTaskId(workTaskId)
                    .questionId(item.getQuestionId())
                    .answerValue(item.getAnswerValue())
                    .build();
            answerRepo.save(answer);
        }

        // If the task was on hold due to a worker comment, the requestor saving
        // answers means they have addressed the concern — clear the comment and
        // resume the task automatically.
        if (task.getStatus() == TaskStatus.HELD && task.getWorkerComment() != null) {
            int updated = workTaskRepo.clearWorkerCommentByTaskId(workTaskId);
            if (updated > 0) {
                log.info("QUESTIONNAIRE saveAnswers | auto-cleared worker hold on task={}", workTaskId);
            }
        }
    }

    /**
     * Returns all answers previously saved for a work task.
     *
     * @param workTaskId the WORK-TASK-X string identifier
     */
    public List<WorkTaskAnswer> getAnswers(String workTaskId) {
        workTaskRepo.findById(workTaskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + workTaskId));
        return answerRepo.findByWorkTaskId(workTaskId);
    }
}

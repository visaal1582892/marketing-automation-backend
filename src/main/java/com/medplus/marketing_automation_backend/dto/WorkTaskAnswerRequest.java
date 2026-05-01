package com.medplus.marketing_automation_backend.dto;

import lombok.Data;

import java.util.List;

/**
 * Payload for batch-submitting questionnaire answers for a work task.
 *
 * Example JSON:
 * {
 *   "answers": [
 *     { "questionId": "QUES-1", "answerValue": "Facebook" },
 *     { "questionId": "QUES-2", "answerValue": "We want to promote our new summer offer." }
 *   ]
 * }
 */
@Data
public class WorkTaskAnswerRequest {

    private List<AnswerItem> answers;

    @Data
    public static class AnswerItem {
        private String questionId;
        private String answerValue;
    }
}

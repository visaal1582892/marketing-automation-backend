package com.medplus.marketing_automation_backend.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A worker's answer to a single dynamic question on a work task.
 * answer_id is a custom String PK: ANS-1, ANS-2, …
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkTaskAnswer {

    /** Custom string PK, e.g. ANS-1 */
    private String answerId;

    /** FK to work_tasks.task_id (e.g. WORK-TASK-5) */
    private String workTaskId;

    /** FK to dynamic_questions.question_id (e.g. QUES-3) */
    private String questionId;

    /** The worker's response — stored as plain text; multi-select as JSON array */
    private String answerValue;
}

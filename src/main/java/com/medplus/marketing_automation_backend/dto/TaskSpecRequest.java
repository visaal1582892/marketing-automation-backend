package com.medplus.marketing_automation_backend.dto;

import lombok.Data;

import java.util.List;

/** One per-deliverable spec row from the Smart Form (Section 4). */
@Data
public class TaskSpecRequest {
    private String granularTaskId;

    /** Dynamic questions for this granular task, answered on the request form. */
    private List<WorkTaskAnswerRequest.AnswerItem> questionnaireAnswers;

    /** Optional task-specific reference files (used in followup-task flow). */
    private List<String> fileUrls;
    private List<String> fileOriginalNames;
}

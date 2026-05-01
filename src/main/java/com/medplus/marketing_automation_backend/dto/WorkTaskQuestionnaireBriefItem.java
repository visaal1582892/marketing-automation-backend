package com.medplus.marketing_automation_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One question + formatted answer for a work task, shown in the request brief drawer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkTaskQuestionnaireBriefItem {

    private String questionId;
    private String questionText;
    /** TEXT, TEXTAREA, SELECT, MULTI_SELECT */
    private String fieldType;
    /** Human-readable value (e.g. multi-select joined with commas). */
    private String answerDisplay;
}

package com.medplus.marketing_automation_backend.domain;

import com.medplus.marketing_automation_backend.enums.FieldType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A reusable question that can be attached to one or more granular task types.
 * question_id is a custom String PK: QUES-1, QUES-2, …
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DynamicQuestion {

    /** Custom string PK, e.g. QUES-1 */
    private String    questionId;

    /** The prompt shown to the worker */
    private String    questionText;

    /** Render hint: TEXT | TEXTAREA | SELECT | MULTI_SELECT */
    private FieldType fieldType;

    /**
     * JSON array of option strings, e.g. ["Facebook","Instagram"].
     * Only used when fieldType is SELECT or MULTI_SELECT.
     */
    private String    options;

    private boolean   isRequired;
}

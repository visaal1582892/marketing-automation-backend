package com.medplus.marketing_automation_backend.dto;

/**
 * Raw row from {@code work_task_answers} joined to {@code dynamic_questions} for brief display.
 */
public record WorkTaskAnswerBriefRow(
        String workTaskId,
        String questionId,
        String questionText,
        String fieldType,
        String answerValue
) {}

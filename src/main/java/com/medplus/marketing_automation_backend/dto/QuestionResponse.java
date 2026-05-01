package com.medplus.marketing_automation_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionResponse {

    private String                 questionId;
    private String                 questionText;
    private String                 fieldType;
    private String                 options;
    /** JSON key "required" — whether the worker must answer */
    private boolean                required;
    private List<MappedTask>       mappedTasks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MappedTask {
        private String granularTaskId;
        private String granularTaskName;
    }
}

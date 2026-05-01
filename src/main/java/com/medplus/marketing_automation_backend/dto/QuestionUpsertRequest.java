package com.medplus.marketing_automation_backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class QuestionUpsertRequest {
    private String        questionText;
    private String        fieldType;
    private String        options;
    private Boolean       isRequired;
    private List<String>  granularTaskIds;
}

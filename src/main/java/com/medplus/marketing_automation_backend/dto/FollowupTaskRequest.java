package com.medplus.marketing_automation_backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class FollowupTaskRequest {
    private List<TaskSpecRequest> specs;
}

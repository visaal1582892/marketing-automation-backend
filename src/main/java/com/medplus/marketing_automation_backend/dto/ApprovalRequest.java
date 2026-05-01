package com.medplus.marketing_automation_backend.dto;

import com.medplus.marketing_automation_backend.enums.ApprovalAction;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApprovalRequest {

    @NotNull
    private ApprovalAction action;

    private String comments;
}

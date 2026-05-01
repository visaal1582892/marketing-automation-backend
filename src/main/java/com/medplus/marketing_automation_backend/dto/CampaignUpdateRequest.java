package com.medplus.marketing_automation_backend.dto;

import com.medplus.marketing_automation_backend.enums.Priority;
import lombok.Data;

@Data
public class CampaignUpdateRequest {
    private Priority priority;
    private String   budgetTier;   // plain display name, e.g. "₹50K – ₹2L"
    private String   keyMessage;
}

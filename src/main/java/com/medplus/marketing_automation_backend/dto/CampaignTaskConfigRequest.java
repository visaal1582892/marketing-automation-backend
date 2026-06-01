package com.medplus.marketing_automation_backend.dto;

import lombok.Data;

import java.util.List;

/** Request body for creating or replacing a campaign task configuration group. */
@Data
public class CampaignTaskConfigRequest {
    private String       campaignTypeId;
    private String       businessVerticalId;
    private String       businessTypeId;
    private String       storeFormatTypeId;
    /** Task IDs to associate with this combination. */
    private List<String> taskIds;
}

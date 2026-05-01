package com.medplus.marketing_automation_backend.domain;

import lombok.*;

/** One row in campaign_deliverables — a per-task spec from the Smart Form. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignDeliverable {
    private Integer specId;
    private Integer campaignId;
    private String  granularTaskId;
    private String  granularTaskName;
}

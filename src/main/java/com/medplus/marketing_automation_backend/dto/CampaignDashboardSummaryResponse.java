package com.medplus.marketing_automation_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Aggregated campaign counts for the requestor dashboard (no pagination).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignDashboardSummaryResponse {

    private long total;
    private long completed;
    private long rejected;
    private long cancelled;
    private long active;
}

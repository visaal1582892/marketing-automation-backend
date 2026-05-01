package com.medplus.marketing_automation_backend.dto;

import lombok.*;

/** Read-only view of a campaign_deliverables row, enriched with names. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliverableResponse {
    private Integer specId;
    private String  granularTaskId;
    private String  granularTaskName;
    /** Status of the linked work_task, e.g. ASSIGNED, HELD, IN_PROGRESS. Null if not yet routed. */
    private String  workTaskStatus;
}

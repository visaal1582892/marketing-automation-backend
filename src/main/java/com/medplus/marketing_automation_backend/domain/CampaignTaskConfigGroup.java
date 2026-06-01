package com.medplus.marketing_automation_backend.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Grouped view of campaign_task_config rows.
 * Multiple DB rows sharing the same (campaignTypeId, businessVerticalId, businessTypeId,
 * storeFormatTypeId) key are collapsed into a single group with a list of tasks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignTaskConfigGroup {

    private String campaignTypeId;
    private String campaignTypeName;
    private String businessVerticalId;
    private String businessVerticalName;
    private String businessTypeId;
    private String businessTypeName;
    private String storeFormatTypeId;
    private String storeFormatTypeName;

    private List<TaskEntry> tasks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskEntry {
        private Long   id;
        private String taskId;
        private String taskName;
        private String status;
    }
}

package com.medplus.marketing_automation_backend.dto;

import lombok.*;

/** Read-only view of a work task deliverable for the campaign brief. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliverableResponse {
    /** Work task ID, e.g. "WORK-TASK-273". */
    private String  taskId;
    private String  granularTaskId;
    private String  granularTaskName;
    /** Current task status, e.g. ASSIGNED, HELD, IN_PROGRESS. */
    private String  workTaskStatus;
}

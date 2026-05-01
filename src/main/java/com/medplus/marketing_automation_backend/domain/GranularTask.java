package com.medplus.marketing_automation_backend.domain;

import com.medplus.marketing_automation_backend.enums.RecordStatus;
import com.medplus.marketing_automation_backend.enums.TaskCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GranularTask {
    private String       taskId;
    private String       taskName;
    private String       taskTypeId;
    private String       taskTypeName;
    private TaskCategory taskCategory;
    private RecordStatus status;
}

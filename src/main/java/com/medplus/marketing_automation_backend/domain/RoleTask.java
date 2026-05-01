package com.medplus.marketing_automation_backend.domain;

import com.medplus.marketing_automation_backend.enums.RecordStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Maps one role to one granular task it is capable of executing. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleTask {
    private Integer      mappingId;
    private String       roleId;
    private String       roleName;
    private String       taskId;
    private String       taskName;
    private RecordStatus status;
}

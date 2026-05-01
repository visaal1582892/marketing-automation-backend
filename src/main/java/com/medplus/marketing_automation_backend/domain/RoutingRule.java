package com.medplus.marketing_automation_backend.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Maps one requirement type to its default handling role. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutingRule {
    private Integer mappingId;
    private String  requirementTypeId;
    private String  requirementTypeName;
    private String  defaultRoleId;
    private String  defaultRoleName;
}

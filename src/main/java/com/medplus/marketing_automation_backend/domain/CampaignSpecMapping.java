package com.medplus.marketing_automation_backend.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Generic mapping row returned by the Campaign Specifications mapping APIs. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignSpecMapping {
    private Integer mappingId;
    private String  parentId;
    private String  parentName;
    private String  childId;
    private String  childName;
}

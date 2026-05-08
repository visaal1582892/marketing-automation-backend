package com.medplus.marketing_automation_backend.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignResponse {

    private Integer       campaignId;
    private Integer       requestorId;
    private String        requestorName;

    private String        departmentId;
    private String        departmentName;
    private String        targetLocation;
    private String        businessObjectiveId; // raw stored ID, for edit form pre-population
    private String        businessObjective;   // resolved display name

    // Task Type (JSON array IDs stored in DB, resolved to display name at service layer)
    private String        taskTypeId;
    private String        taskTypeName;

    private String        audienceTypeId;   // raw JSON array of IDs, for edit form pre-population
    private String        audienceName;     // resolved display names, for brief display
    private String        language;         // resolved display names
    private String        languageIds;      // raw JSON array of IDs

    private String        hasOffer;
    private String        offerTypeId;
    private String        offerTypeName;
    private String        keyMessage;
    private String        supportingProofId;   // raw stored ID, for edit form pre-population
    private String        supportingProof;     // resolved display name

    private String        tone;             // resolved display names
    private String        toneIds;          // raw JSON array of IDs

    private String        priority;

    private String        budgetTierId;        // raw stored ID, for edit form pre-population
    private String        budgetTier;          // resolved display name
    private String        vendorRequired;
    private String        vendorType;          // resolved display names
    private String        vendorTypeIds;       // raw JSON array of IDs

    private String        kpiTypeId;           // raw stored ID, for edit form pre-population
    private String        kpiType;             // resolved display name
    private String        expectedOutputId;    // raw stored ID, for edit form pre-population
    private String        expectedOutput;      // resolved display name

    private String        status;
    private String        routingNotes;
    private Boolean       flaggedInconsistency;
    private String        inconsistencyReason;

    private String        rejectionReason;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** True when the calling user has bookmarked this campaign. */
    private Boolean       bookmarked;

    // Enriched — deliverable specs & work tasks for detail view
    private List<DeliverableResponse> deliverables;
    private List<WorkTaskResponse>    workTasks;

    // Task summary counts (populated in list views, avoids N+1 work-task queries)
    private Integer taskCount;
    private Integer completedTaskCount;
    private Boolean hasRework;
    private Boolean hasQcReview;

    // Campaign-level supporting files uploaded by the requestor
    private List<String> fileUrls;

    // Original filenames parallel to fileUrls (index-matched)
    private List<String> fileOriginalNames;
}

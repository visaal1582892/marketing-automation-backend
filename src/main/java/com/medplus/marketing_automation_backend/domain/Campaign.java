package com.medplus.marketing_automation_backend.domain;

import com.medplus.marketing_automation_backend.enums.CampaignStatus;
import com.medplus.marketing_automation_backend.enums.Priority;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Campaign {

    private Integer campaignId;
    private Integer requestorId;
    private String  requestorName;

    // Section 1
    private String departmentId;
    private String departmentName;
    private String targetLocation;
    private String businessObjectiveId;   // raw stored ID, for edit-form pre-population
    private String businessObjective;     // resolved display name, e.g. "Lead Generation"

    // Section 2 — Task Type (JSON array of task_type IDs, resolved at service layer)
    private String taskTypeId;
    private String taskTypeName;

    // Section 3 — comma-separated names for multi-select
    private String audienceTypeId;        // e.g. "Retail Customers,Franchise Owners"
    private String language;              // e.g. "English,Hindi"

    // Section 4
    private String hasOffer;
    private String offerTypeId;           // now holds the name string, e.g. "Discount %"
    private String offerTypeName;         // alias — same value
    private String keyMessage;
    private String supportingProofId;     // raw stored ID, for edit-form pre-population
    private String supportingProof;       // resolved display name, e.g. "Store Count"

    // Section 5 — comma-separated names for multi-select
    private String tone;                  // e.g. "Informative,Premium"

    // Section 6 — Priority kept as enum (system-level, not admin-configurable)
    private Priority priority;

    // Section 7
    private String budgetTierId;          // raw stored ID, for edit-form pre-population
    private String budgetTier;            // resolved display name, e.g. "₹50K – ₹2L"
    private String vendorRequired;
    private String vendorType;            // comma-separated names, e.g. "Printing,Media Buying"

    // Section 8
    private String kpiTypeId;             // raw stored ID, for edit-form pre-population
    private String kpiType;              // resolved display name, e.g. "Leads"
    private String expectedOutputId;      // raw stored ID, for edit-form pre-population
    private String expectedOutput;       // resolved display name, e.g. "100 – 500 Leads"

    // System
    private CampaignStatus status;
    private String         routingNotes;

    // Module 2 — inconsistency flag
    private Boolean flaggedInconsistency;
    private String  inconsistencyReason;

    private String        rejectionReason;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Aggregated task counts (populated by list queries, not the detail query)
    private Integer taskCount;
    private Integer completedTaskCount;
    private Boolean hasRework;
    private Boolean hasQcReview;
}

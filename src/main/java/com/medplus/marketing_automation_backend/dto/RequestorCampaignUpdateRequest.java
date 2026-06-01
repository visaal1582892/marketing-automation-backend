package com.medplus.marketing_automation_backend.dto;

import com.medplus.marketing_automation_backend.enums.Priority;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Payload for the requestor's own-campaign edit endpoint.
 * Mirrors CampaignRequest but without mandatory validations — all fields are
 * optional so the caller may send only what changed.
 * Existing tasks are NEVER modified; newTaskSpecs adds additional deliverables.
 */
@Data
public class RequestorCampaignUpdateRequest {

    // Section 1
    private String departmentId;
    private String targetLocation;
    private String businessObjective;
    private String storeId;
    private String contactNumber;

    // Section 3 – multi-select: array of master IDs + optional free-text element
    private List<String> audienceTypeId;
    private List<String> language;

    // Section 4
    private String hasOffer;
    private String offerTypeId;
    private String keyMessage;
    private String supportingProof;

    // Section 5 – multi-select
    private List<String> tone;

    // Section 6
    private LocalDate deadline;
    private Priority   priority;

    // Section 7
    private String       budgetTier;
    private String       vendorRequired;
    private List<String> vendorType;

    // Section 8
    private String kpiType;
    private String expectedOutput;

    // Additional tasks to append (existing tasks are untouched)
    private List<TaskSpecRequest> newTaskSpecs;

    // New campaign-level files to attach
    private List<String> newFileUrls;

    // Original filenames parallel to newFileUrls (index-matched)
    private List<String> newFileOriginalNames;

    // Existing file URLs the requestor wants removed
    private List<String> removedFileUrls;
}

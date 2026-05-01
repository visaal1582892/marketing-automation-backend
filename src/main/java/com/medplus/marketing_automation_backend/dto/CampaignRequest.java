package com.medplus.marketing_automation_backend.dto;

import com.medplus.marketing_automation_backend.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CampaignRequest {

    // Section 1 – Requestor Details
    private String departmentId;
    private String targetLocation;
    private String businessObjective;     // master item ID, or free-text if "Other"

    // Section 2 – Campaign Type
    @NotBlank
    private String requirementTypeId;     // master item ID, or free-text if "Other"

    // Section 3 – Audience (JSON array of IDs; free-text appended for "Other")
    private List<String> audienceTypeId;  // e.g. ["1","2"] or ["1","Custom Audience"]
    private List<String> language;        // e.g. ["1","2"] or ["1","Custom Language"]

    // Section 4 – Offer & Messaging
    private String hasOffer;
    private String offerTypeId;           // master item ID, or free-text if "Other"
    private String keyMessage;
    private String supportingProof;       // master item ID, or free-text if "Other"

    // Section 5 – Tone (JSON array of IDs)
    private List<String> tone;            // e.g. ["1","2"] or ["1","Custom Tone"]

    // Section 6 – Timelines
    private LocalDate deadline;
    private Priority  priority = Priority.MEDIUM;

    // Section 7 – Budget & Execution
    private String budgetTier;            // master item ID, or free-text if "Other"
    private String vendorRequired;
    private List<String> vendorType;      // e.g. ["1","2"] or ["1","Custom Vendor"]

    // Section 8 – KPIs
    private String kpiType;              // master item ID, or free-text if "Other"
    private String expectedOutput;       // master item ID, or free-text if "Other"

    // Per-task deliverable specs
    private List<TaskSpecRequest> taskSpecs;

    // Campaign-level supporting files (uploaded URLs)
    private List<String> fileUrls;
}

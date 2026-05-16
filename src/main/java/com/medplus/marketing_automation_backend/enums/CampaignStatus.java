package com.medplus.marketing_automation_backend.enums;

public enum CampaignStatus {
    IN_PROGRESS("In Progress"),
    MANAGER_QC_REVIEW("Manager QC Review"),
    REQUESTOR_QC_REVIEW("Requestor QC Review"),
    COMPLETED("Completed"),
    REJECTED("Rejected"),
    CANCELLED("Cancelled");

    private final String label;
    CampaignStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
}

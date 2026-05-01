package com.medplus.marketing_automation_backend.enums;

public enum CampaignStatus {
    IN_PROGRESS("In Progress"),
    QC_REVIEW("QC Review"),
    COMPLETED("Completed"),
    REJECTED("Rejected"),
    CANCELLED("Cancelled");

    private final String label;
    CampaignStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
}

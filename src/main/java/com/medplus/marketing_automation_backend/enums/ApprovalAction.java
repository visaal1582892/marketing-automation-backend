package com.medplus.marketing_automation_backend.enums;

public enum ApprovalAction {
    APPROVED("Approved"),
    NEEDS_REWORK("Needs Rework"),
    REJECTED("Rejected"),
    /** Sent by the requestor after the task is COMPLETED — triggers another rework cycle. */
    REQUESTOR_REWORK("Requestor Rework"),
    /** Task was placed on hold (by a worker via comment, or by the manager). */
    HELD("Held"),
    /** Task was taken off hold and resumed or re-routed. */
    UNHOLD("Unhold"),
    /** Task was cancelled before work began. */
    CANCELLED("Cancelled");

    private final String label;
    ApprovalAction(String label) { this.label = label; }
    public String getLabel() { return label; }
}

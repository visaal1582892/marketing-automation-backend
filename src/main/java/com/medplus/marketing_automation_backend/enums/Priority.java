package com.medplus.marketing_automation_backend.enums;

public enum Priority {
    HIGH("High (24–48 hrs)"),
    MEDIUM("Medium (3–5 days)"),
    LOW("Low (1 week+)");

    private final String label;
    Priority(String label) { this.label = label; }
    public String getLabel() { return label; }
}

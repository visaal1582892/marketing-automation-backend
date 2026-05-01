package com.medplus.marketing_automation_backend.enums;

public enum LeadStatus {
    HOT("Hot"),
    WARM("Warm"),
    COLD("Cold");

    private final String label;
    LeadStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
}

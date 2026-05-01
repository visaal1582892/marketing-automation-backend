package com.medplus.marketing_automation_backend.enums;

public enum KeyMessage {
    TRUST_CREDIBILITY("Trust / Credibility"),
    PRICE_ADVANTAGE("Price Advantage"),
    CONVENIENCE("Convenience"),
    GROWTH_OPPORTUNITY("Growth Opportunity"),
    HEALTH_AWARENESS("Health Awareness"),
    COMPLIANCE_SAFETY("Compliance / Safety Guidelines");

    private final String label;
    KeyMessage(String label) { this.label = label; }
    public String getLabel() { return label; }
}

package com.medplus.marketing_automation_backend.enums;

public enum GeographyTier {
    TIER_1_CITIES("Tier 1 Cities"),
    TIER_2_CITIES("Tier 2 Cities"),
    TIER_3_RURAL("Tier 3 / Rural");

    private final String label;
    GeographyTier(String label) { this.label = label; }
    public String getLabel() { return label; }
}

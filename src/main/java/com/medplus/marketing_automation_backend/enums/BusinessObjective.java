package com.medplus.marketing_automation_backend.enums;

public enum BusinessObjective {
    LEAD_GENERATION("Lead Generation"),
    WALK_INS_FOOTFALL("Walk-ins / Store Footfall"),
    BRAND_AWARENESS("Brand Awareness"),
    PRODUCT_LAUNCH("Product Launch"),
    OFFER_PROMOTION("Offer Promotion"),
    RECRUITMENT("Recruitment"),
    INTERNAL_COMMUNICATION("Internal Communication"),
    OPERATIONAL_COMPLIANCE_SAFETY("Operational Compliance / Safety");

    private final String label;
    BusinessObjective(String label) { this.label = label; }
    public String getLabel() { return label; }
}

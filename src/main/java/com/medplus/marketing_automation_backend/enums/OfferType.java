package com.medplus.marketing_automation_backend.enums;

public enum OfferType {
    DISCOUNT_PERCENT("Discount %"),
    FLAT_DISCOUNT("Flat Discount"),
    FREE_CHECKUP("Free Checkup"),
    BUNDLE_OFFER("Bundle Offer"),
    FRANCHISE_ROI_PITCH("Franchise ROI Pitch"),
    SALARY_HIRING_OFFER("Salary / Hiring Offer");

    private final String label;
    OfferType(String label) { this.label = label; }
    public String getLabel() { return label; }
}

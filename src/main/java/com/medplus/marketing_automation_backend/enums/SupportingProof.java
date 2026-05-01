package com.medplus.marketing_automation_backend.enums;

public enum SupportingProof {
    STORE_COUNT("Store Count"),
    CUSTOMER_BASE("Customer Base"),
    YEARS_IN_MARKET("Years in Market"),
    DOCTOR_RECOMMENDATION("Doctor Recommendation"),
    TESTIMONIALS_AVAILABLE("Testimonials Available");

    private final String label;
    SupportingProof(String label) { this.label = label; }
    public String getLabel() { return label; }
}

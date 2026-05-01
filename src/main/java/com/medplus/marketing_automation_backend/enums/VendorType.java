package com.medplus.marketing_automation_backend.enums;

public enum VendorType {
    PRINTING("Printing"),
    VIDEO_PRODUCTION("Video Production"),
    INFLUENCER("Influencer"),
    MEDIA_BUYING("Media Buying");

    private final String label;
    VendorType(String label) { this.label = label; }
    public String getLabel() { return label; }
}

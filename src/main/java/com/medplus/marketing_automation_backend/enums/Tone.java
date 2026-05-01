package com.medplus.marketing_automation_backend.enums;

public enum Tone {
    INFORMATIVE("Informative"),
    EMOTIONAL("Emotional"),
    URGENT_CTA_DRIVEN("Urgent / CTA Driven"),
    PREMIUM("Premium"),
    TRUST_LED("Trust-led"),
    AUTHORITATIVE_INSTRUCTIONAL("Authoritative / Instructional");

    private final String label;
    Tone(String label) { this.label = label; }
    public String getLabel() { return label; }
}

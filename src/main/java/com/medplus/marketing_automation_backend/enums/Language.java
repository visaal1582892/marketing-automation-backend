package com.medplus.marketing_automation_backend.enums;

public enum Language {
    ENGLISH("English"),
    HINDI("Hindi"),
    TELUGU("Telugu"),
    TAMIL("Tamil"),
    KANNADA("Kannada"),
    MULTI_LANGUAGE("Multi-language");

    private final String label;
    Language(String label) { this.label = label; }
    public String getLabel() { return label; }
}

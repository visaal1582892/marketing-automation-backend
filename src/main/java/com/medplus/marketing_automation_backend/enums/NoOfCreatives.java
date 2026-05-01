package com.medplus.marketing_automation_backend.enums;

public enum NoOfCreatives {
    ONE_TO_TWO("1–2"),
    THREE_TO_FIVE("3–5"),
    FIVE_TO_TEN("5–10"),
    TEN_PLUS("10+");

    private final String label;
    NoOfCreatives(String label) { this.label = label; }
    public String getLabel() { return label; }
}

package com.medplus.marketing_automation_backend.enums;

public enum TaskCategory {
    DIGITAL("Digital"),
    OFFLINE("Offline");

    private final String label;

    TaskCategory(String label) { this.label = label; }

    public String getLabel() { return label; }
}

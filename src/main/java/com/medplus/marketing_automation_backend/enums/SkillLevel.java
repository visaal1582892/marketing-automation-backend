package com.medplus.marketing_automation_backend.enums;

public enum SkillLevel {
    SENIOR("Senior"),
    JUNIOR("Junior"),
    INTERN("Intern");

    private final String label;
    SkillLevel(String label) { this.label = label; }
    public String getLabel() { return label; }
}

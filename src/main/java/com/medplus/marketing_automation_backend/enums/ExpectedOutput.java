package com.medplus.marketing_automation_backend.enums;

public enum ExpectedOutput {
    UNDER_100_LEADS("<100 Leads"),
    HUNDRED_TO_500_LEADS("100–500 Leads"),
    FIVE_HUNDRED_TO_1000_LEADS("500–1000 Leads"),
    ABOVE_1000_LEADS("1000+ Leads");

    private final String label;
    ExpectedOutput(String label) { this.label = label; }
    public String getLabel() { return label; }
}

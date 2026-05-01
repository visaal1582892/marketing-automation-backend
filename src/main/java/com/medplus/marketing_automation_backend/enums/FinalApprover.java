package com.medplus.marketing_automation_backend.enums;

public enum FinalApprover {
    DEPARTMENT_HEAD("Department Head"),
    MARKETING_HEAD("Marketing Head"),
    REGIONAL_MANAGER("Regional Manager");

    private final String label;
    FinalApprover(String label) { this.label = label; }
    public String getLabel() { return label; }
}

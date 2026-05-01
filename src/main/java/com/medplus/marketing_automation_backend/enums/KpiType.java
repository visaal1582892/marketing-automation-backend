package com.medplus.marketing_automation_backend.enums;

public enum KpiType {
    LEADS("Leads"),
    CPL("CPL"),
    FOOTFALL("Footfall"),
    SALES("Sales"),
    ENGAGEMENT("Engagement"),
    REACH("Reach"),
    TICKET_RESOLUTION_COMPLIANCE("Ticket Resolution / Compliance");

    private final String label;
    KpiType(String label) { this.label = label; }
    public String getLabel() { return label; }
}

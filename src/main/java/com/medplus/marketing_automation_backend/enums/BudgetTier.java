package com.medplus.marketing_automation_backend.enums;

public enum BudgetTier {
    NO_BUDGET_ORGANIC("No Budget (Organic)"),
    UNDER_50K("< ₹50K"),
    FIFTY_K_TO_2L("₹50K – ₹2L"),
    TWO_L_TO_10L("₹2L – ₹10L"),
    ABOVE_10L("₹10L+");

    private final String label;
    BudgetTier(String label) { this.label = label; }
    public String getLabel() { return label; }
}

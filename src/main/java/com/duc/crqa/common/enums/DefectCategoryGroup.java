package com.duc.crqa.common.enums;

public enum DefectCategoryGroup {
    SIT_EXECUTION("1. SIT Execution"),
    UAT_LEAKAGE_REGRESSION("2. UAT Leakage & Regression"),
    PRODUCTION_LEAKAGE("3. Production Leakage"),
    NON_FUNCTIONAL_ENVIRONMENT("4. Non-Functional & Environment"),
    SCOPE_REQUIREMENTS("5. Scope & Requirements");

    private final String label;

    DefectCategoryGroup(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

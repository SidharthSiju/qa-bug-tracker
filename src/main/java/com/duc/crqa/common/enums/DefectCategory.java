package com.duc.crqa.common.enums;

import java.util.Locale;

public enum DefectCategory {
    SIT_OPEN(DefectCategoryGroup.SIT_EXECUTION, "SIT - Open"),
    SIT_FIXED_VERIFIED(DefectCategoryGroup.SIT_EXECUTION, "SIT - Fixed & Verified"),
    SIT_DEFERRED(DefectCategoryGroup.SIT_EXECUTION, "SIT - Deferred"),
    SIT_LEAKAGE_OPEN(DefectCategoryGroup.UAT_LEAKAGE_REGRESSION,
            "SIT Leakage (Missed in SIT / Caught in UAT - Open)"),
    SIT_LEAKAGE_FIXED(DefectCategoryGroup.UAT_LEAKAGE_REGRESSION,
            "SIT Leakage (Missed in SIT / Caught in UAT - Fixed)"),
    SIT_REGRESSION_REOPENED_IN_UAT(DefectCategoryGroup.UAT_LEAKAGE_REGRESSION,
            "SIT Regression (Fixed in SIT / Reopened in UAT)"),
    UAT_LEAKAGE_CAUGHT_IN_PROD(DefectCategoryGroup.PRODUCTION_LEAKAGE,
            "UAT Leakage (Missed in SIT & UAT / Caught in Prod)"),
    PROD_REGRESSION(DefectCategoryGroup.PRODUCTION_LEAKAGE,
            "Prod Regression (Fixed in SIT/UAT / Reopened in Prod)"),
    ENVIRONMENT_ISSUE(DefectCategoryGroup.NON_FUNCTIONAL_ENVIRONMENT, "Environment Issue"),
    DEPLOYMENT_PIPELINE_ISSUE(DefectCategoryGroup.NON_FUNCTIONAL_ENVIRONMENT, "Deployment / Pipeline Issue"),
    DATA_ISSUE(DefectCategoryGroup.NON_FUNCTIONAL_ENVIRONMENT, "Data Issue"),
    GENERIC_OUT_OF_SCOPE(DefectCategoryGroup.SCOPE_REQUIREMENTS, "Generic / Out of Scope Issue"),
    REQUIREMENT_GAP(DefectCategoryGroup.SCOPE_REQUIREMENTS, "Requirement Gap / Change Request"),
    NOT_A_BUG(DefectCategoryGroup.SCOPE_REQUIREMENTS, "Not a Bug / Invalid");

    private final DefectCategoryGroup group;
    private final String label;

    DefectCategory(DefectCategoryGroup group, String label) {
        this.group = group;
        this.label = label;
    }

    public DefectCategoryGroup getGroup() {
        return group;
    }

    public String getLabel() {
        return label;
    }

    public static DefectCategory fromLabel(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        for (DefectCategory category : values()) {
            if (category.label.equalsIgnoreCase(normalized)) {
                return category;
            }
        }
        String asName = normalized.toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (DefectCategory category : values()) {
            if (category.name().equals(asName)) {
                return category;
            }
        }
        return null;
    }
}

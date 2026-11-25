package com.duc.crqa.common.enums;

import java.util.Locale;

public enum TestPhase {
    DEV("Development"),
    SIT("System Integration Testing"),
    UAT("User Acceptance Testing"),
    STG("Staging"),
    PROD("Production");

    private final String label;

    TestPhase(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static TestPhase fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "DEV", "DEVELOPMENT" -> DEV;
            case "SIT", "SYSTEM", "SYSTEM TEST", "SYSTEM INTEGRATION",
                    "SYSTEM INTEGRATION TESTING", "SYSTEM INTEGRATION TESTING (SIT)" -> SIT;
            case "UAT", "USER ACCEPTANCE", "USER ACCEPTANCE TESTING",
                    "USER ACCEPTANCE TESTING (UAT)" -> UAT;
            case "STG", "STAGE", "STAGING" -> STG;
            case "PROD", "PRODUCTION" -> PROD;
            default -> null;
        };
    }
}

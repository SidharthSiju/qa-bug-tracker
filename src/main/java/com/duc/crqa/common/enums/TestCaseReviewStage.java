package com.duc.crqa.common.enums;

public enum TestCaseReviewStage {
    QA_LEAD("QA Lead", "QA_LEAD"),
    SA_LEAD("SA Lead", "SA_LEAD"),
    DDM("DDM", "DDM"),
    BUSINESS("Business", "Business");

    private final String label;
    private final String activityLabel;

    TestCaseReviewStage(String label, String activityLabel) {
        this.label = label;
        this.activityLabel = activityLabel;
    }

    public String label() {
        return label;
    }

    public String activityLabel() {
        return activityLabel;
    }

    public TestCaseReviewStatus pendingStatus() {
        return switch (this) {
            case QA_LEAD -> TestCaseReviewStatus.PENDING_QA_LEAD;
            case SA_LEAD -> TestCaseReviewStatus.PENDING_SA_LEAD;
            case DDM -> TestCaseReviewStatus.PENDING_DDM;
            case BUSINESS -> TestCaseReviewStatus.PENDING_BUSINESS;
        };
    }
}

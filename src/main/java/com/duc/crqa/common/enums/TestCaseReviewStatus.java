package com.duc.crqa.common.enums;

public enum TestCaseReviewStatus {
    NOT_SUBMITTED("Not submitted"),
    PENDING_QA_LEAD("Pending QA Lead review"),
    PENDING_SA_LEAD("Pending SA Lead review"),
    PENDING_DDM("Pending DDM review"),
    PENDING_BUSINESS("Pending Business review"),
    CLOSED("Closed");

    private final String label;

    TestCaseReviewStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public TestCaseReviewStage stage() {
        return switch (this) {
            case PENDING_QA_LEAD -> TestCaseReviewStage.QA_LEAD;
            case PENDING_SA_LEAD -> TestCaseReviewStage.SA_LEAD;
            case PENDING_DDM -> TestCaseReviewStage.DDM;
            case PENDING_BUSINESS -> TestCaseReviewStage.BUSINESS;
            case NOT_SUBMITTED, CLOSED -> null;
        };
    }

    public TestCaseReviewStatus next() {
        return switch (this) {
            case PENDING_QA_LEAD -> PENDING_SA_LEAD;
            case PENDING_SA_LEAD -> PENDING_DDM;
            case PENDING_DDM -> PENDING_BUSINESS;
            case PENDING_BUSINESS -> CLOSED;
            case NOT_SUBMITTED -> PENDING_QA_LEAD;
            case CLOSED -> CLOSED;
        };
    }
}

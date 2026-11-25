package com.duc.crqa.api.dto;

import com.duc.crqa.common.enums.Priority;
import com.duc.crqa.common.enums.TestCaseStatus;
import com.duc.crqa.testcase.entity.TestCase;
import java.time.LocalDateTime;

public record TestCaseDto(
        Long id,
        String testCaseNumber,
        Long crId,
        String crNumber,
        Long cycleId,
        Integer cycleNumber,
        String title,
        String description,
        String precondition,
        String testSteps,
        String expectedResult,
        String actualResult,
        Priority priority,
        String labels,
        String issueType,
        String storyLink,
        String epicLink,
        String issueLink,
        TestCaseStatus status,
        Long executedById,
        LocalDateTime executedAt) {

    public static TestCaseDto from(TestCase testCase) {
        return new TestCaseDto(
                testCase.getId(),
                testCase.getTestCaseNumber(),
                testCase.getCr() == null ? null : testCase.getCr().getId(),
                testCase.getCr() == null ? null : testCase.getCr().getCrNumber(),
                testCase.getCycle() == null ? null : testCase.getCycle().getId(),
                testCase.getCycle() == null ? null : testCase.getCycle().getCycleNumber(),
                testCase.getTitle(),
                testCase.getDescription(),
                testCase.getPrecondition(),
                testCase.getTestSteps(),
                testCase.getExpectedResult(),
                testCase.getActualResult(),
                testCase.getPriority(),
                testCase.getLabels(),
                testCase.getIssueType(),
                testCase.getStoryLink(),
                testCase.getEpicLink(),
                testCase.getIssueLink(),
                testCase.getStatus(),
                testCase.getExecutedBy(),
                testCase.getExecutedAt());
    }
}

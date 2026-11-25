package com.duc.crqa.api.dto;

import com.duc.crqa.bug.entity.Bug;
import com.duc.crqa.common.enums.BugStatus;
import com.duc.crqa.common.enums.DefectCategory;
import com.duc.crqa.common.enums.Priority;
import com.duc.crqa.common.enums.Resolution;
import com.duc.crqa.common.enums.Severity;
import com.duc.crqa.common.enums.TestPhase;
import java.time.LocalDateTime;

public record BugDto(
        Long id,
        String bugNumber,
        Long crId,
        String crNumber,
        Long testCaseId,
        String testCaseNumber,
        String title,
        String description,
        String stepsToReproduce,
        String expectedResult,
        String actualResult,
        Severity severity,
        Priority priority,
        BugStatus status,
        String environment,
        String application,
        String module,
        String sprint,
        DefectCategory defectCategory,
        TestPhase testPhaseFound,
        String externalDefectId,
        Long reportedById,
        String reportedByName,
        Long assignedToId,
        String assignedToName,
        LocalDateTime reportedAt,
        LocalDateTime assignedAt,
        LocalDateTime fixedAt,
        LocalDateTime retestedAt,
        LocalDateTime closedAt,
        Resolution resolution,
        String resolutionComment,
        Long version) {

    public static BugDto from(Bug bug) {
        return new BugDto(
                bug.getId(),
                bug.getBugNumber(),
                bug.getCr() == null ? null : bug.getCr().getId(),
                bug.getCr() == null ? null : bug.getCr().getCrNumber(),
                bug.getTestCase() == null ? null : bug.getTestCase().getId(),
                bug.getTestCase() == null ? null : bug.getTestCase().getTestCaseNumber(),
                bug.getTitle(),
                bug.getDescription(),
                bug.getStepsToReproduce(),
                bug.getExpectedResult(),
                bug.getActualResult(),
                bug.getSeverity(),
                bug.getPriority(),
                bug.getStatus(),
                bug.getEnvironment(),
                bug.getApplication(),
                bug.getModule(),
                bug.getSprint(),
                bug.getDefectCategory(),
                bug.getTestPhaseFound(),
                bug.getExternalDefectId(),
                bug.getReportedBy() == null ? null : bug.getReportedBy().getId(),
                bug.getReportedBy() == null ? null : bug.getReportedBy().displayLabel(),
                bug.getAssignedTo() == null ? null : bug.getAssignedTo().getId(),
                bug.getAssignedTo() == null ? null : bug.getAssignedTo().displayLabel(),
                bug.getReportedAt(),
                bug.getAssignedAt(),
                bug.getFixedAt(),
                bug.getRetestedAt(),
                bug.getClosedAt(),
                bug.getResolution(),
                bug.getResolutionComment(),
                bug.getVersion());
    }
}

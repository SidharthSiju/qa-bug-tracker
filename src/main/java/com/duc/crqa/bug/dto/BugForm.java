package com.duc.crqa.bug.dto;

import com.duc.crqa.common.enums.BugStatus;
import com.duc.crqa.common.enums.DefectCategory;
import com.duc.crqa.common.enums.Priority;
import com.duc.crqa.common.enums.Severity;
import com.duc.crqa.common.enums.TestPhase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class BugForm {

    @NotNull(message = "Change request is required")
    private Long crId;

    private Long testCaseId;

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Description is required")
    private String description;

    private String stepsToReproduce;

    private String expectedResult;

    private String actualResult;

    @NotNull(message = "Severity is required")
    private Severity severity = Severity.MAJOR;

    @NotNull(message = "Priority is required")
    private Priority priority = Priority.P2;

    @NotBlank(message = "Environment is required")
    private String environment;

    private String application;

    private String module;

    private String sprint;

    private DefectCategory defectCategory;

    private TestPhase testPhaseFound;

    private String externalDefectId;

    private BugStatus status;

    private Long assignedToId;

    private Long reportedById;

    private Long version;
}

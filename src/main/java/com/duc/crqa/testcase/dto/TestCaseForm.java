package com.duc.crqa.testcase.dto;

import com.duc.crqa.common.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TestCaseForm {

    @NotNull(message = "Change request is required")
    private Long crId;

    private Long cycleId;

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    private String precondition;

    private String testSteps;

    private String expectedResult;

    private Priority priority;

    private String labels;

    private String issueType;

    private String issueLink;
}

package com.duc.crqa.cr.dto;

import com.duc.crqa.common.enums.Complexity;
import com.duc.crqa.common.enums.CrPriority;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
@NoArgsConstructor
public class CrForm {

    @NotBlank(message = "Epic number is required")
    private String crNumber;

    @NotBlank(message = "CR name is required")
    private String crName;

    private String description;

    private Long projectId;

    private String environment;

    private CrPriority priority = CrPriority.MEDIUM;

    private Complexity complexity = Complexity.MEDIUM;

    private String sprintName;

    private String storyNumber;

    private Long developmentOwnerId;

    private Long qaLeadId;

    private Long saLeadId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate plannedStartDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate plannedEndDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate actualStartDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate actualEndDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dacSubmitDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate hldSubmitDate;

    private Long version;
}

package com.duc.crqa.api.dto;

import com.duc.crqa.common.enums.Complexity;
import com.duc.crqa.common.enums.CrPriority;
import com.duc.crqa.common.enums.CrStatus;
import com.duc.crqa.cr.entity.ChangeRequest;
import java.time.LocalDateTime;

public record CrDto(
        Long id,
        String crNumber,
        String crName,
        String description,
        String projectCode,
        String environment,
        CrPriority priority,
        Complexity complexity,
        String sprintName,
        String storyNumber,
        Long developmentOwnerId,
        String developmentOwnerName,
        Long qaLeadId,
        String qaLeadName,
        CrStatus status,
        LocalDateTime plannedStartDate,
        LocalDateTime plannedEndDate,
        LocalDateTime actualStartDate,
        LocalDateTime actualEndDate,
        LocalDateTime dacSubmitDate,
        LocalDateTime hldSubmitDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long version) {

    public static CrDto from(ChangeRequest cr) {
        return new CrDto(
                cr.getId(),
                cr.getCrNumber(),
                cr.getCrName(),
                cr.getDescription(),
                cr.getProject() == null ? null : cr.getProject().getProjectCode(),
                cr.getEnvironment(),
                cr.getPriority(),
                cr.getComplexity(),
                cr.getSprintName(),
                cr.getStoryNumber(),
                cr.getDevelopmentOwner() == null ? null : cr.getDevelopmentOwner().getId(),
                cr.getDevelopmentOwner() == null ? null : cr.getDevelopmentOwner().displayLabel(),
                cr.getQaLead() == null ? null : cr.getQaLead().getId(),
                cr.getQaLead() == null ? null : cr.getQaLead().displayLabel(),
                cr.getStatus(),
                cr.getPlannedStartDate(),
                cr.getPlannedEndDate(),
                cr.getActualStartDate(),
                cr.getActualEndDate(),
                cr.getDacSubmitDate(),
                cr.getHldSubmitDate(),
                cr.getCreatedAt(),
                cr.getUpdatedAt(),
                cr.getVersion());
    }
}

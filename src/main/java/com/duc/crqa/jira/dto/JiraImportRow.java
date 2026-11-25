package com.duc.crqa.jira.dto;

import java.io.Serializable;

/**
 * A row of the JIRA review table: everything the reviewer needs to decide whether to import the issue, plus whether
 * that change request already exists (so the import will insert or update it) and whether the row needs attention.
 */
public record JiraImportRow(
        String storyNumber,
        String crNumber,
        String epicSummary,
        String epicType,
        String epicStatus,
        String storySummary,
        String storyType,
        String storyStatus,
        String priority,
        String created,
        String cmPm,
        String cmSa,
        String admPm,
        String admSa,
        String kickoffDate,
        String dacSubmitDate,
        String hldSubmitDate,
        String rfbtDate,
        String developer,
        String qa,
        boolean existing,
        /** True when the RFBT date is before today, i.e. the row is flagged in red as needing attention. */
        boolean attention) implements Serializable {
}

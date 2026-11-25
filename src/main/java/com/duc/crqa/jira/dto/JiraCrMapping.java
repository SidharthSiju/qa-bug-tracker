package com.duc.crqa.jira.dto;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * The change-request values derived from one JIRA issue.
 *
 * <ul>
 *   <li>{@code crNumber} — the epic number, from {@code parent.key}</li>
 *   <li>{@code crName} — the story summary, from {@code fields.summary}</li>
 *   <li>{@code storyNumber} — the story number, i.e. the issue key</li>
 *   <li>{@code description} — "CM PM : CM SA" (customfield_10037 : customfield_10038)</li>
 *   <li>{@code dacSubmitDate} — customfield_10146, {@code hldSubmitDate} — customfield_10105</li>
 * </ul>
 */
public record JiraCrMapping(
        String crNumber,
        String crName,
        String storyNumber,
        String description,
        LocalDate dacSubmitDate,
        LocalDate hldSubmitDate
        ) implements Serializable {
}

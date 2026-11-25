package com.duc.crqa.jira.dto;

import java.io.Serializable;
import java.util.Map;

/**
 * One issue as returned by the JIRA search API, reduced to the parts the importer needs.
 *
 * <p>The epic details come from the issue's {@code parent} object: {@code parent.key}, {@code parent.fields.summary},
 * {@code parent.fields.issuetype.name} and {@code parent.fields.status.name}. All the {@code customfield_*} values
 * requested in the JIRA configuration are kept in {@link #customFields()} keyed by field id.
 */
public record JiraIssue(
        String key,
        String summary,
        String issueType,
        String status,
        String priority,
        String assignee,
        String project,
        String created,
        String epicNumber,
        String epicName,
        String epicType,
        String epicStatus,
        Map<String, String> customFields) implements Serializable {

    public String customField(String name) {
        return customFields == null ? null : customFields.get(name);
    }
}

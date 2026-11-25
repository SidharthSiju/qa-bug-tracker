package com.duc.crqa.jira.service;

import com.duc.crqa.common.enums.Complexity;
import com.duc.crqa.common.enums.CrPriority;
import com.duc.crqa.common.util.CsvUtil;
import com.duc.crqa.cr.entity.ChangeRequest;
import com.duc.crqa.cr.service.ChangeRequestService;
import com.duc.crqa.jira.client.JiraClient;
import com.duc.crqa.jira.client.JiraConnection;
import com.duc.crqa.jira.dto.JiraCrMapping;
import com.duc.crqa.jira.dto.JiraImportResult;
import com.duc.crqa.jira.dto.JiraImportRow;
import com.duc.crqa.jira.dto.JiraIssue;
import com.duc.crqa.jira.dto.JiraSearchForm;
import com.duc.crqa.jira.entity.JiraConfig;
import com.duc.crqa.jira.exception.JiraException;
import com.duc.crqa.project.service.ProjectService;
import com.duc.crqa.user.entity.User;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Turns JIRA issues into change requests: runs the configured JQL, derives the change-request values from each issue
 * and hands them to {@link ChangeRequestService} for the insert-or-update.
 */
@Service
public class JiraImportService {

    private static final Logger log = LoggerFactory.getLogger(JiraImportService.class);

    /** CM PM — becomes the first half of the change request description. */
    public static final String CUSTOM_CM_PM = "customfield_10037";
    /** CM SA — becomes the second half of the change request description. */
    public static final String CUSTOM_CM_SA = "customfield_10038";
    public static final String CUSTOM_PROJECT_MANAGER = "customfield_10034";
    public static final String CUSTOM_SOLUTION_ARCHITECT = "customfield_10033";
    public static final String CUSTOM_KICKOFF_DATE = "customfield_10159";
    /** DAC submit date. */
    public static final String CUSTOM_DAC_SUBMIT_DATE = "customfield_10146";
    /** HLD submit date. */
    public static final String CUSTOM_HLD_SUBMIT_DATE = "customfield_10105";
    public static final String CUSTOM_MISSED_RFBT = "customfield_10150";
    /** RFBT date. */
    public static final String CUSTOM_RFBT_DATE = "customfield_10066";
    /** Developer — shown in the review table's Developer column. */
    public static final String CUSTOM_DEVELOPER = "customfield_10143";

    /**
     * Fields the import cannot work without (epic details, the change request name, the description and dates, and the
     * two review columns driven by JIRA). They are requested even when an ADMIN trims the configured field list.
     */
    private static final List<String> REQUIRED_FIELDS = List.of(
            "parent",
            "summary",
            CUSTOM_CM_PM,
            CUSTOM_CM_SA,
            CUSTOM_DAC_SUBMIT_DATE,
            CUSTOM_HLD_SUBMIT_DATE,
            CUSTOM_RFBT_DATE,
            CUSTOM_DEVELOPER);

    private static final Map<String, String> CUSTOM_FIELD_LABELS;

    static {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(CUSTOM_RFBT_DATE, "RFBT Date");
        labels.put(CUSTOM_CM_PM, "CM PM");
        labels.put(CUSTOM_CM_SA, "CM SA");
        labels.put(CUSTOM_SOLUTION_ARCHITECT, "Solution Architect");
        labels.put(CUSTOM_PROJECT_MANAGER, "Project Manager");
        labels.put(CUSTOM_KICKOFF_DATE, "Kick-off Date");
        labels.put(CUSTOM_DAC_SUBMIT_DATE, "DAC submit date");
        labels.put(CUSTOM_HLD_SUBMIT_DATE, "HLD Submit Date");
        labels.put(CUSTOM_MISSED_RFBT, "Missed RFBT");
        labels.put(CUSTOM_DEVELOPER, "Developer");
        CUSTOM_FIELD_LABELS = Collections.unmodifiableMap(labels);
    }

    /** One line of the documented JIRA → change request mapping. */
    public record FieldDoc(String jiraField, String meaning, String target) {
    }

    /** The mapping table shown on the JIRA Configuration and import screens. */
    public static List<FieldDoc> fieldDocs() {
        return List.of(
                new FieldDoc("parent.key", "Epic number", "Epic Number (cr_number)"),
                new FieldDoc("parent.fields.summary", "Epic summary", "review table only"),
                new FieldDoc("parent.fields.issuetype.name", "Epic issue type", "review table only"),
                new FieldDoc("parent.fields.status.name", "Epic issue status", "review table only"),
                new FieldDoc("key", "Story number", "Story Number (story_number)"),
                new FieldDoc("summary", "Story summary", "Name (cr_name)"),
                new FieldDoc("issuetype.name", "Story issue type", "review table only"),
                new FieldDoc("status.name", "Story issue status", "review table only"),
                new FieldDoc("priority.name", "Priority", "review table only"),
                new FieldDoc("created", "Created", "review table only"),
                new FieldDoc(CUSTOM_CM_PM, CUSTOM_FIELD_LABELS.get(CUSTOM_CM_PM),
                        "Description (first part) and review table"),
                new FieldDoc(CUSTOM_CM_SA, CUSTOM_FIELD_LABELS.get(CUSTOM_CM_SA),
                        "Description (second part) and review table"),
                new FieldDoc(CUSTOM_PROJECT_MANAGER, CUSTOM_FIELD_LABELS.get(CUSTOM_PROJECT_MANAGER),
                        "review table only (ADM PM)"),
                new FieldDoc(CUSTOM_SOLUTION_ARCHITECT, CUSTOM_FIELD_LABELS.get(CUSTOM_SOLUTION_ARCHITECT),
                        "review table only (ADM SA)"),
                new FieldDoc(CUSTOM_KICKOFF_DATE, CUSTOM_FIELD_LABELS.get(CUSTOM_KICKOFF_DATE),
                        "review table only"),
                new FieldDoc(CUSTOM_DAC_SUBMIT_DATE, CUSTOM_FIELD_LABELS.get(CUSTOM_DAC_SUBMIT_DATE),
                        "DAC submit date and review table"),
                new FieldDoc(CUSTOM_HLD_SUBMIT_DATE, CUSTOM_FIELD_LABELS.get(CUSTOM_HLD_SUBMIT_DATE),
                        "HLD submit date and review table"),
                new FieldDoc(CUSTOM_RFBT_DATE, CUSTOM_FIELD_LABELS.get(CUSTOM_RFBT_DATE), "review table only"),
                new FieldDoc(CUSTOM_MISSED_RFBT, CUSTOM_FIELD_LABELS.get(CUSTOM_MISSED_RFBT), "retrieved only"),
                new FieldDoc(CUSTOM_DEVELOPER, CUSTOM_FIELD_LABELS.get(CUSTOM_DEVELOPER),
                        "review table: Developer column"),
                new FieldDoc("(change request)", "QA Lead held in this application",
                        "review table: QA column for change requests that already exist"));
    }

    private final JiraClient jiraClient;
    private final JiraConfigService jiraConfigService;
    private final ChangeRequestService changeRequestService;

    public JiraImportService(JiraClient jiraClient,
                             JiraConfigService jiraConfigService,
                             ChangeRequestService changeRequestService) {
        this.jiraClient = jiraClient;
        this.jiraConfigService = jiraConfigService;
        this.changeRequestService = changeRequestService;
    }

    /** The documented meaning of every custom field the integration understands, in display order. */
    public static Map<String, String> customFieldLabels() {
        return CUSTOM_FIELD_LABELS;
    }

    /** Runs the configured JQL for the selected status and from-date. */
    public List<JiraIssue> search(JiraSearchForm form) {
        if (!jiraConfigService.isEnabled()) {
            throw new JiraException("The JIRA integration is switched off. An ADMIN can enable it under "
                    + "Administration -> JIRA Configuration.");
        }
        if (!jiraConfigService.isConfigured()) {
            throw new JiraException("JIRA is not configured. An ADMIN must set the API URL, email and API token "
                    + "under Administration -> JIRA Configuration.");
        }
        JiraConfig config = jiraConfigService.current();
        String jql;
        if (form.getStoryNumber() != null && !form.getStoryNumber().isBlank()) {
            String keys = form.getStoryNumber().replaceAll("[^A-Za-z0-9,\\-]", "");   // strip anything unsafe
            jql = "key in (" + keys + ")";
        } else {
            if (form.getFromDate() == null || form.getStatus() == null || form.getStatus().isBlank()) {
                throw new JiraException("Enter a story number, or both a created-from date and a status.");
            }
            jql = buildJql(config.getJql(), form.getStatus(), form.getFromDate());
        }
        JiraConnection connection = new JiraConnection(config.getBaseUrl(), config.getEmail(),
                config.getApiToken(), config.getCaCertificate(), jiraConfigService.timeoutSeconds());
        return jiraClient.search(connection, jql, requestFields(), jiraConfigService.maxResults());
    }

    /**
     * The same search, but run by the user's browser: the browser calls JIRA (which this server may not be able to
     * reach) and posts the response back, and it is mapped here so the review table, the import and the change-request
     * values are identical to the server-side path.
     */
    public List<JiraIssue> readIssues(String browserResponse) {
        if (!jiraConfigService.isEnabled()) {
            throw new JiraException("The JIRA integration is switched off. An ADMIN can enable it under "
                    + "Administration -> JIRA Configuration.");
        }
        List<JiraIssue> issues = jiraClient.readIssues(browserResponse, requestFields());
        log.info("JIRA search run by the browser returned {} issue(s)", issues.size());
        return issues;
    }

    /** The fields a search must request: the configured ones plus the ones the mapping cannot work without. */
    public List<String> requestFields() {
        return withRequiredFields(jiraConfigService.fieldNames());
    }

    /**
     * The columns of the change-request bulk-import template ({@code samples/cr_upload_template.csv}), so an export can
     * be fed straight back into Change Requests &rarr; Bulk Import.
     */
    public static final String BULK_IMPORT_HEADER =
            "epicNumber,crName,projectCode,environment,sprintName,priority,complexity,saLeadUsername,"
                    + "developmentOwnerUsername,qaLeadUsername,plannedStartDate,plannedEndDate,description";

    /** The fetched issues the given keys select, in the order they were fetched. */
    public List<JiraIssue> selected(List<String> keys, List<JiraIssue> issues) {
        Map<String, JiraIssue> byKey = new LinkedHashMap<>();
        for (JiraIssue issue : issues) {
            byKey.put(issue.key(), issue);
        }
        List<JiraIssue> picked = new ArrayList<>();
        for (String key : new LinkedHashSet<>(keys)) {
            JiraIssue issue = byKey.get(key);
            if (issue != null) {
                picked.add(issue);
            }
        }
        return picked;
    }

    /**
     * The selected issues as a bulk-import CSV: the same columns as the template, holding exactly what importing them
     * from JIRA would store (the default project, environment SIT, priority/complexity MEDIUM, the CM PM/CM SA
     * description, and no owners — the review columns are not written by the import either).
     */
    public String bulkImportCsv(List<JiraIssue> issues) {
        StringBuilder csv = new StringBuilder(BULK_IMPORT_HEADER).append("\r\n");
        for (JiraIssue issue : issues) {
            JiraCrMapping mapping = toMapping(issue);
            List<String> cells = List.of(
                    nullToEmpty(mapping.crNumber()),
                    nullToEmpty(mapping.crName()),
                    ProjectService.DEFAULT_PROJECT_CODE,
                    ChangeRequestService.JIRA_DEFAULT_ENVIRONMENT,
                    "",
                    CrPriority.MEDIUM.name(),
                    Complexity.MEDIUM.name(),
                    "",
                    "",
                    "",
                    "",
                    "",
                    nullToEmpty(mapping.description()));
            csv.append(cells.stream().map(CsvUtil::escape).collect(Collectors.joining(","))).append("\r\n");
        }
        return csv.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** The JIRA search endpoint the browser has to call (the configured URL, or one derived from it). */
    public String searchEndpoint() {
        return JiraClient.searchEndpoint(jiraConfigService.baseUrl());
    }

    /** Adds the fields the mapping needs to the configured ones, keeping the configured order. */
    static List<String> withRequiredFields(List<String> configured) {
        List<String> fields = new ArrayList<>(configured);
        for (String required : REQUIRED_FIELDS) {
            if (!fields.contains(required)) {
                fields.add(required);
            }
        }
        return fields;
    }

    /** Replaces the {@code {{status}}} and {@code {{createdFrom}}} placeholders of the JQL template. */
    public String buildJql(String template, String status, LocalDate fromDate) {
        String statusValue = status == null ? "" : status.trim();
        if (statusValue.contains(" ") && !statusValue.startsWith("\"")) {
            statusValue = "\"" + statusValue + "\"";
        }
        String jql = template == null ? "" : template;
        jql = jql.replace("{{status}}", statusValue);
        jql = jql.replace("{{createdFrom}}", fromDate == null ? "" : fromDate.toString());
        return jql.trim();
    }

    /**
     * Maps one issue onto change-request values: the epic number comes from the parent's key (falling back to the
     * issue's own key when it has no parent), the name from the issue summary, and the dates and description from the
     * configured custom fields.
     */
    public JiraCrMapping toMapping(JiraIssue issue) {
        String crNumber = firstNonBlank(issue.epicNumber(), issue.key());
        String crName = firstNonBlank(issue.summary(), issue.key());
        String description = joinNonBlank(" : ", issue.customField(CUSTOM_CM_PM), issue.customField(CUSTOM_CM_SA));
        return new JiraCrMapping(
                crNumber,
                crName,
                issue.key(),
                description,
                parseDate(issue.customField(CUSTOM_DAC_SUBMIT_DATE)),
                parseDate(issue.customField(CUSTOM_HLD_SUBMIT_DATE)));
    }

    /**
     * Builds a review row: the JIRA values, what the issue will become in this application, and whether that change
     * request already exists.
     *
     * <p><strong>Developer</strong> is the displayName of the JIRA {@code customfield_10143}, falling back to the
     * development owner already recorded on an existing change request. <strong>QA</strong> is the QA Lead already
     * recorded on an existing change request — JIRA does not hold it, and the import never changes either of them.
     */
    public JiraImportRow toRow(JiraIssue issue) {
        JiraCrMapping mapping = toMapping(issue);
        Optional<ChangeRequest> current =
                changeRequestService.findByCrNumberAndCrName(mapping.crNumber(), mapping.crName());

        User developmentOwner = current.map(ChangeRequest::getDevelopmentOwner).orElse(null);
        User qaLead = current.map(ChangeRequest::getQaLead).orElse(null);

        String rfbtRaw = issue.customField(CUSTOM_RFBT_DATE);
        LocalDate rfbt = parseDate(rfbtRaw);
        // An RFBT date that has already passed needs attention, so the reviewer sees the row highlighted.
        boolean attention = rfbt != null && rfbt.isBefore(LocalDate.now());

        return new JiraImportRow(
                issue.key(),
                mapping.crNumber(),
                issue.epicName(),
                issue.epicType(),
                issue.epicStatus(),
                mapping.crName(),
                issue.issueType(),
                issue.status(),
                issue.priority(),
                formatDate(issue.created()),
                issue.customField(CUSTOM_CM_PM),
                issue.customField(CUSTOM_CM_SA),
                issue.customField(CUSTOM_PROJECT_MANAGER),
                issue.customField(CUSTOM_SOLUTION_ARCHITECT),
                formatDate(issue.customField(CUSTOM_KICKOFF_DATE)),
                formatDate(issue.customField(CUSTOM_DAC_SUBMIT_DATE)),
                formatDate(issue.customField(CUSTOM_HLD_SUBMIT_DATE)),
                displayDate(rfbt, rfbtRaw),
                firstNonBlank(issue.customField(CUSTOM_DEVELOPER),
                        developmentOwner == null ? null : developmentOwner.displayLabel()),
                qaLead == null ? null : qaLead.displayLabel(),
                current.isPresent(),
                attention);
    }

    /**
     * Imports the selected issues. Each issue is imported in its own transaction, so a failing row is reported and the
     * remaining rows still import.
     */
    public JiraImportResult importIssues(List<String> keys, List<JiraIssue> issues) {
        Map<String, JiraIssue> byKey = new LinkedHashMap<>();
        for (JiraIssue issue : issues) {
            byKey.put(issue.key(), issue);
        }

        int created = 0;
        int updated = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        for (String key : new LinkedHashSet<>(keys)) {
            JiraIssue issue = byKey.get(key);
            if (issue == null) {
                failed++;
                errors.add(key + ": not part of the fetched result - run the search again");
                continue;
            }
            try {
                if (changeRequestService.importFromJira(toMapping(issue))) {
                    created++;
                } else {
                    updated++;
                }
            } catch (Exception ex) {
                failed++;
                errors.add(key + ": " + ex.getMessage());
                log.warn("JIRA import failed for {}: {}", key, ex.getMessage());
            }
        }

        return new JiraImportResult(created, updated, failed, errors);
    }

    private static String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first.trim();
    }

    /** Renders a JIRA date field as {@code yyyy-MM-dd}, keeping the original text when it cannot be parsed. */
    private static String formatDate(String value) {
        return displayDate(parseDate(value), value);
    }

    private static String displayDate(LocalDate parsed, String rawValue) {
        if (parsed != null) {
            return parsed.toString();
        }
        return rawValue == null || rawValue.isBlank() ? null : rawValue.trim();
    }

    private static String joinNonBlank(String separator, String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                parts.add(value.trim());
            }
        }
        return parts.isEmpty() ? null : String.join(separator, parts);
    }

    /** JIRA dates arrive as {@code yyyy-MM-dd}, with a time, or as a full ISO timestamp — accept all of them. */
    static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return LocalDate.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            // fall through to the date-time variants
        }
        try {
            return OffsetDateTime.parse(trimmed).toLocalDate();
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return ZonedDateTime.parse(trimmed).toLocalDate();
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(trimmed).toLocalDate();
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        if (trimmed.length() >= 10) {
            try {
                return LocalDate.parse(trimmed.substring(0, 10));
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
        return null;
    }
}

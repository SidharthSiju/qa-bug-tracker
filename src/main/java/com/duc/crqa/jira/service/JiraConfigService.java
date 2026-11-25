package com.duc.crqa.jira.service;

import com.duc.crqa.config.JiraProperties;
import com.duc.crqa.jira.entity.JiraConfig;
import com.duc.crqa.jira.repository.JiraConfigRepository;
import com.duc.crqa.security.CurrentUserService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Holds the single global JIRA configuration row (id = 1), seeded from {@code app.jira.*} the first time it is used.
 * Everything is normalised on read and write so a stale or hand-edited value can never break an import run.
 */
@Service
public class JiraConfigService {

    public static final Long SETTINGS_ID = 1L;
    public static final int MAX_RESULTS_LIMIT = 500;
    public static final int MAX_TIMEOUT_SECONDS = 300;

    /**
     * The status options shipped before the current default. A stored value equal to this one was never customised, so
     * it is upgraded to the new default instead of being kept.
     */
    private static final String LEGACY_SHIPPED_STATUSES =
            "To Do,In Progress,Ready for QA,In QA,Done,Closed,Reopened";

    private final JiraConfigRepository jiraConfigRepository;
    private final JiraProperties jiraProperties;
    private final CurrentUserService currentUserService;

    private volatile JiraConfig cached;

    public JiraConfigService(JiraConfigRepository jiraConfigRepository,
                             JiraProperties jiraProperties,
                             CurrentUserService currentUserService) {
        this.jiraConfigRepository = jiraConfigRepository;
        this.jiraProperties = jiraProperties;
        this.currentUserService = currentUserService;
    }

    public JiraConfig current() {
        JiraConfig local = cached;
        if (local == null) {
            synchronized (this) {
                local = cached;
                if (local == null) {
                    local = load();
                    cached = local;
                }
            }
        }
        return local;
    }

    @Transactional
    public void update(boolean enabled,
                       boolean clientSide,
                       String baseUrl,
                       String email,
                       String apiToken,
                       String caCertificate,
                       Integer maxResults,
                       Integer timeoutSeconds,
                       String jql,
                       String fieldList,
                       String statusList) {
        JiraConfig config = jiraConfigRepository.findById(SETTINGS_ID).orElseGet(() -> {
            JiraConfig created = new JiraConfig();
            created.setId(SETTINGS_ID);
            return created;
        });
        config.setEnabled(enabled);
        config.setClientSide(clientSide);
        // Clearing a connection field restores the application.yml default rather than leaving the import unusable.
        config.setBaseUrl(normalizeText(baseUrl, jiraProperties.getBaseUrl()));
        config.setEmail(normalizeText(email, jiraProperties.getEmail()));
        config.setApiToken(normalizeText(apiToken, jiraProperties.getApiToken()));
        config.setCaCertificate(normalizeText(caCertificate, jiraProperties.getCaCertificate()));
        config.setMaxResults(normalizeMaxResults(maxResults));
        config.setTimeoutSeconds(normalizeTimeout(timeoutSeconds));
        config.setJql(normalizeText(jql, jiraProperties.getJql()));
        config.setFieldList(normalizeText(fieldList, jiraProperties.getFields()));
        config.setStatusList(normalizeText(statusList, jiraProperties.getStatuses()));
        config.setUpdatedAt(LocalDateTime.now());
        config.setUpdatedBy(currentUserService.currentUserId());
        cached = jiraConfigRepository.save(config);
    }

    /** True when an ADMIN has switched the integration on. */
    public boolean isEnabled() {
        return current().isEnabled();
    }

    /**
     * True when the JIRA search must be run by the user's browser rather than by this server — for a deployment whose
     * server has no route to JIRA (no internet access) while the users' browsers do.
     */
    public boolean isClientSide() {
        return current().isClientSide();
    }

    /** True when the minimum settings needed to call JIRA are present. */
    public boolean isConfigured() {
        JiraConfig config = current();
        return !isBlank(config.getBaseUrl()) && !isBlank(config.getEmail()) && !isBlank(config.getApiToken());
    }

    /** True when the integration may actually be used: switched on and configured. */
    public boolean isReady() {
        return isEnabled() && isConfigured();
    }

    public String baseUrl() {
        return current().getBaseUrl();
    }

    public String email() {
        return current().getEmail();
    }

    public String apiToken() {
        return current().getApiToken();
    }

    /** The PEM CA certificate to trust for JIRA calls, or {@code null} to use the JVM's default truststore. */
    public String caCertificate() {
        return current().getCaCertificate();
    }

    public int maxResults() {
        return normalizeMaxResults(current().getMaxResults());
    }

    public int timeoutSeconds() {
        return normalizeTimeout(current().getTimeoutSeconds());
    }

    public String jql() {
        return current().getJql();
    }

    /** The configured JIRA fields as a list, e.g. {@code parent}, {@code summary}, {@code customfield_10037}. */
    public List<String> fieldNames() {
        return split(current().getFieldList());
    }

    /** The status options offered by the import search form. */
    public List<String> statuses() {
        return split(current().getStatusList());
    }

    /** The values shipped in application.yml, used by the "Restore defaults" button. */
    public JiraProperties defaults() {
        return jiraProperties;
    }

    private JiraConfig load() {
        JiraConfig config = jiraConfigRepository.findById(SETTINGS_ID).orElse(null);
        if (config == null) {
            JiraConfig created = new JiraConfig();
            created.setId(SETTINGS_ID);
            created.setEnabled(jiraProperties.isEnabled());
            created.setClientSide(jiraProperties.isClientSide());
            created.setBaseUrl(trimToNull(jiraProperties.getBaseUrl()));
            created.setEmail(trimToNull(jiraProperties.getEmail()));
            created.setApiToken(trimToNull(jiraProperties.getApiToken()));
            created.setCaCertificate(trimToNull(jiraProperties.getCaCertificate()));
            created.setMaxResults(normalizeMaxResults(jiraProperties.getMaxResults()));
            created.setTimeoutSeconds(normalizeTimeout(jiraProperties.getTimeoutSeconds()));
            created.setJql(jiraProperties.getJql());
            created.setFieldList(jiraProperties.getFields());
            created.setStatusList(jiraProperties.getStatuses());
            return jiraConfigRepository.save(created);
        }
        // Never let an unexpected stored value break an import run.
        config.setMaxResults(normalizeMaxResults(config.getMaxResults()));
        config.setTimeoutSeconds(normalizeTimeout(config.getTimeoutSeconds()));
        // The connection settings fall back to application.yml until an ADMIN overrides them on the screen.
        config.setBaseUrl(normalizeText(config.getBaseUrl(), jiraProperties.getBaseUrl()));
        config.setEmail(normalizeText(config.getEmail(), jiraProperties.getEmail()));
        config.setApiToken(normalizeText(config.getApiToken(), jiraProperties.getApiToken()));
        config.setCaCertificate(normalizeText(config.getCaCertificate(), jiraProperties.getCaCertificate()));
        config.setJql(normalizeText(config.getJql(), jiraProperties.getJql()));
        config.setFieldList(normalizeText(config.getFieldList(), jiraProperties.getFields()));
        if (config.getStatusList() != null
                && config.getStatusList().trim().equalsIgnoreCase(LEGACY_SHIPPED_STATUSES)) {
            config.setStatusList(jiraProperties.getStatuses());
        }
        config.setStatusList(normalizeText(config.getStatusList(), jiraProperties.getStatuses()));
        return jiraConfigRepository.save(config);
    }

    private int normalizeMaxResults(Integer value) {
        if (value == null || value < 1) {
            return clamp(jiraProperties.getMaxResults(), 1, MAX_RESULTS_LIMIT);
        }
        return clamp(value, 1, MAX_RESULTS_LIMIT);
    }

    private int normalizeTimeout(Integer value) {
        if (value == null || value < 1) {
            return clamp(jiraProperties.getTimeoutSeconds(), 1, MAX_TIMEOUT_SECONDS);
        }
        return clamp(value, 1, MAX_TIMEOUT_SECONDS);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String normalizeText(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed != null ? trimmed : fallback;
    }

    private static List<String> split(String value) {
        List<String> values = new ArrayList<>();
        if (value == null) {
            return values;
        }
        for (String token : new LinkedHashSet<>(List.of(value.split(",")))) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Kept for callers that want a lower-cased, comparable form of a configured value. */
    public static String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

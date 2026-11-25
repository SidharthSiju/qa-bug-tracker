package com.duc.crqa.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Defaults for the JIRA integration, bound to {@code app.jira.*} in application.yml. These seed the editable
 * {@code IT_jira_config} row the first time the JIRA Configuration screen is used; from then on the stored values
 * win (the ADMIN can change everything without a restart).
 */
@Component
@ConfigurationProperties(prefix = "app.jira")
@Getter
@Setter
public class JiraProperties {

    /** Whether the JIRA integration is switched on; when off the "Import from JIRA" button is not shown. */
    private boolean enabled = true;

    /**
     * Run the JIRA search in the user's browser instead of on the server. Needed when this server cannot reach JIRA
     * (no internet route) while the user's browser can. The browser must be allowed to call JIRA (CORS) - see the
     * JIRA Configuration screen.
     */
    private boolean clientSide = false;

    /** JIRA base URL, e.g. {@code https://jira.example.com}. May also be given as a full search endpoint. */
    private String baseUrl = "https://okducagile.atlassian.net/rest/api/3/search/jql";

    /** Account email used for JIRA basic authentication. */
    private String email = "";

    /** JIRA API token used for JIRA basic authentication. */
    private String apiToken = "";

    /**
     * PEM certificate (or chain) of the authority that signs the JIRA server's certificate, used when this JVM does
     * not already trust it — a private CA, or a reverse proxy such as nginx in front of JIRA. Blank uses the JVM's
     * default truststore.
     */
    private String caCertificate = "";

    /** Maximum number of issues to fetch from JIRA. */
    private int maxResults = 50;

    /** Request timeout in seconds for JIRA calls. */
    private int timeoutSeconds = 30;

    /**
     * JQL template. {@code {{status}}} and {@code {{createdFrom}}} are replaced with the values selected on the
     * import search form.
     */
    private String jql = "project = OKQABUG AND issuetype in (Story,Task,Bug) and status = {{status}} "
            + "AND created >= \"{{createdFrom}}\" ORDER BY created ASC";

    /** Comma-separated JIRA fields to request for every issue. */
    private String fields = "parent,summary,issuetype,status,priority,assignee,project,created,"
            + "customfield_10066,customfield_10037,customfield_10038,customfield_10033,customfield_10034,"
            + "customfield_10159,customfield_10146,customfield_10105,customfield_10150,customfield_10143";

    /** Comma-separated options offered by the "status" dropdown on the import search form. */
    private String statuses = "INITIATION,FEASIBILITY,DAC Build,HLD Build,HLG Approval,DEVELOPMENT,SIT,UAT";
}

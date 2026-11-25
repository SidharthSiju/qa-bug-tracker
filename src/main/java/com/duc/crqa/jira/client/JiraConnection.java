package com.duc.crqa.jira.client;

/** The connection settings used for one JIRA call. */
public record JiraConnection(String baseUrl, String email, String apiToken, String caCertificate,
                             int timeoutSeconds) {
}

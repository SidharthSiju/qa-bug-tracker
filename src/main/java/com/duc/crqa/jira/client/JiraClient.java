package com.duc.crqa.jira.client;

import com.duc.crqa.jira.dto.JiraIssue;
import com.duc.crqa.jira.exception.JiraException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Minimal JIRA REST client: authenticates with basic auth (email + API token) and searches issues with JQL.
 *
 * <p>The configured URL is used as the JIRA base URL unless it already points at a REST endpoint, in which case it is
 * used verbatim — so both {@code https://jira.example.com} and
 * {@code https://jira.example.com/rest/api/2/search} work.
 */
@Component
public class JiraClient {

    private static final Logger log = LoggerFactory.getLogger(JiraClient.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final ObjectMapper objectMapper;

    public JiraClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Runs the JQL and returns up to {@code maxResults} issues, paging through JIRA when more than one page is needed.
     */
    public List<JiraIssue> search(JiraConnection connection, String jql, List<String> fields, int maxResults) {
        String endpoint = searchEndpoint(connection.baseUrl());
        // JIRA Cloud's newer /search/jql API pages with an opaque token instead of startAt/total.
        boolean tokenPaging = endpoint.contains("/search/jql");

        List<JiraIssue> issues = new ArrayList<>();
        int startAt = 0;
        int remaining = Math.max(1, maxResults);
        String nextPageToken = null;

        while (remaining > 0) {
            int chunk = Math.min(MAX_PAGE_SIZE, remaining);
            JsonNode root = requestPage(connection, endpoint, jql, fields, startAt, chunk, nextPageToken);
            JsonNode nodes = root.path("issues");
            if (!nodes.isArray() || nodes.isEmpty()) {
                break;
            }

            for (JsonNode node : nodes) {
                issues.add(toIssue(node, fields));
            }

            int received = nodes.size();
            remaining -= received;

            if (tokenPaging) {
                // The token API says explicitly whether more pages exist, so the request size is never used to guess.
                String token = text(root, "nextPageToken");
                if (token == null || root.path("isLast").asBoolean(false)) {
                    break;
                }
                nextPageToken = token;
            } else {
                if (received < chunk) {
                    break;
                }
                startAt += received;
                if (startAt >= root.path("total").asLong(issues.size())) {
                    break;
                }
            }
        }

        log.info("JIRA search returned {} issue(s) for jql={}", issues.size(), jql);
        return issues;
    }

    /**
     * Reads a search response that a browser fetched from JIRA. The browser runs the same JQL through the same
     * endpoint and merges the pages, so the result carries the same {@code issues} array; the mapping below is
     * therefore shared with the server-side path and cannot drift from it.
     */
    public List<JiraIssue> readIssues(String responseBody, List<String> fields) {
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody == null ? "" : responseBody);
        } catch (Exception ex) {
            throw new JiraException("The response JIRA sent to the browser could not be read as JSON", ex);
        }
        JsonNode nodes = root.isArray() ? root : root.path("issues");
        List<JiraIssue> issues = new ArrayList<>();
        if (nodes.isArray()) {
            for (JsonNode node : nodes) {
                issues.add(toIssue(node, fields));
            }
        }
        return issues;
    }

    /**
     * The endpoint to call: the configured URL is used verbatim when it already points at a REST endpoint, otherwise
     * one is derived from it. Atlassian Cloud is served by the token-paged v3 search; Server/Data Center keeps the
     * offset-paged v2 search.
     */
    public static String searchEndpoint(String baseUrl) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.contains("/rest/")) {
            return base;
        }
        return base + (base.contains("atlassian.net") ? "/rest/api/3/search/jql" : "/rest/api/2/search");
    }

    private JsonNode requestPage(JiraConnection connection, String endpoint, String jql, List<String> fields,
                                 int startAt, int chunk, String nextPageToken) {
        if (connection.baseUrl() == null || connection.baseUrl().isBlank()) {
            throw new JiraException("JIRA API URL is not configured. Set it under Administration -> JIRA Configuration.");
        }

        URI uri = searchUri(endpoint, jql, fields, startAt, chunk, nextPageToken);
        String body;
        try {
            body = restClient(connection).get()
                    .uri(uri)
                    .headers(headers -> headers.setBasicAuth(connection.email(), connection.apiToken()))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            throw new JiraException(describeResponse(connection, ex));
        } catch (ResourceAccessException ex) {
            log.warn("JIRA call to {} failed: {}", connection.baseUrl(), ex.getMessage());
            throw new JiraException(unreachableMessage(connection.baseUrl(), ex), ex);
        } catch (JiraException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new JiraException("JIRA request failed: " + ex.getMessage(), ex);
        }

        try {
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            throw new JiraException("JIRA returned a response that could not be read as JSON", ex);
        }
    }

    /**
     * Why a JIRA call could not be made, in terms an operator can act on. "Could not reach JIRA" on its own is not
     * actionable: a DNS failure, a refused connection, a firewall and an untrusted TLS certificate all look the same
     * from the outside, and they have completely different fixes.
     */
    static String unreachableMessage(String baseUrl, ResourceAccessException ex) {
        Throwable specific = ex.getMostSpecificCause();
        String detail = specific.getMessage() == null ? "" : specific.getMessage().trim().replaceAll("\\s+", " ");
        String name = specific.getClass().getSimpleName();
        boolean tls = false;
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof SSLException
                    || (cause.getMessage() != null && cause.getMessage().contains("PKIX"))) {
                tls = true;
                name = "SSLHandshakeException";
                break;
            }
        }
        String reason = name + (detail.isEmpty() ? "" : ": " + detail);
        if (reason.length() > 220) {
            reason = reason.substring(0, 220) + "...";
        }
        String advice = tls
                ? "JIRA's TLS certificate is not trusted by this JVM - paste the certificate that signed it into "
                        + "\"CA certificate\" on Administration -> JIRA Configuration, import it into the JDK "
                        + "truststore (keytool -importcert -cacerts), or point -Djavax.net.ssl.trustStore at a "
                        + "truststore that contains it"
                : "check the URL, then that this server can reach JIRA (DNS, firewall, and an outbound proxy if one is "
                        + "required: -Dhttps.proxyHost/-Dhttps.proxyPort); if this server has no route to JIRA at all, "
                        + "tick \"Connect to JIRA from the user's browser\" on Administration -> JIRA Configuration";
        return "Could not reach JIRA at " + baseUrl + " (" + reason + "). " + advice + ".";
    }

    private RestClient restClient(JiraConnection connection) {
        SSLContext sslContext = sslContext(connection.caCertificate());
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection openConnection, String httpMethod) throws IOException {
                super.prepareConnection(openConnection, httpMethod);
                if (sslContext != null && openConnection instanceof HttpsURLConnection https) {
                    https.setSSLSocketFactory(sslContext.getSocketFactory());
                }
            }
        };
        Duration timeout = Duration.ofSeconds(Math.max(1, connection.timeoutSeconds()));
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    /**
     * A trust context built from the configured CA certificate, or {@code null} when none is configured (the JVM's
     * default truststore is then used). It lets an ADMIN trust a private CA — or a reverse proxy such as nginx in
     * front of JIRA — without changing the JVM's truststore.
     */
    private SSLContext sslContext(String caCertificate) {
        if (caCertificate == null || caCertificate.isBlank()) {
            return null;
        }
        try {
            Collection<? extends Certificate> certificates = CertificateFactory.getInstance("X.509")
                    .generateCertificates(new ByteArrayInputStream(caCertificate.getBytes(StandardCharsets.UTF_8)));
            if (certificates.isEmpty()) {
                throw new JiraException("The configured JIRA CA certificate is not a PEM certificate (expected "
                        + "-----BEGIN CERTIFICATE----- ... -----END CERTIFICATE-----)");
            }
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            int index = 0;
            for (Certificate certificate : certificates) {
                trustStore.setCertificateEntry("jira-ca-" + index++, certificate);
            }
            TrustManagerFactory trustManagerFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustManagerFactory.getTrustManagers(), null);
            return context;
        } catch (JiraException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new JiraException("The configured JIRA CA certificate could not be used: " + ex.getMessage(), ex);
        }
    }

    private URI searchUri(String endpoint, String jql, List<String> fields, int startAt, int chunk,
                          String nextPageToken) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(endpoint)
                .queryParam("jql", jql)
                .queryParam("maxResults", chunk)
                .queryParam("fields", String.join(",", fields));

        if (nextPageToken != null) {
            builder.queryParam("nextPageToken", nextPageToken);
        } else if (!endpoint.contains("/search/jql")) {
            builder.queryParam("startAt", startAt);
        }

        return builder.build().encode().toUri();
    }

    private String describeResponse(JiraConnection connection, RestClientResponseException ex) {
        String detail = null;
        try {
            JsonNode body = objectMapper.readTree(ex.getResponseBodyAsString());
            JsonNode messages = body.path("errorMessages");
            if (messages.isArray() && !messages.isEmpty()) {
                List<String> text = new ArrayList<>();
                messages.forEach(message -> text.add(message.asText()));
                detail = String.join("; ", text);
            }
        } catch (Exception ignored) {
            // fall through to the raw body
        }
        if (detail == null || detail.isBlank()) {
            String raw = ex.getResponseBodyAsString();
            detail = raw == null || raw.isBlank() ? ex.getStatusText()
                    : raw.substring(0, Math.min(raw.length(), 300));
        }
        return "JIRA returned " + ex.getStatusCode().value() + " for " + connection.baseUrl() + ": " + detail;
    }

    private JiraIssue toIssue(JsonNode node, List<String> fields) {
        JsonNode issueFields = node.path("fields");
        JsonNode parent = issueFields.path("parent");
        JsonNode parentFields = parent.path("fields");

        Map<String, String> customFields = new LinkedHashMap<>();
        if (fields != null) {
            for (String field : fields) {
                if (field.startsWith("customfield_")) {
                    customFields.put(field, scalar(issueFields.get(field)));
                }
            }
        }

        return new JiraIssue(
                text(node, "key"),
                text(issueFields, "summary"),
                text(issueFields.path("issuetype"), "name"),
                text(issueFields.path("status"), "name"),
                text(issueFields.path("priority"), "name"),
                text(issueFields.path("assignee"), "displayName"),
                text(issueFields.path("project"), "name"),
                text(issueFields, "created"),
                text(parent, "key"),
                text(parentFields, "summary"),
                text(parentFields.path("issuetype"), "name"),
                text(parentFields.path("status"), "name"),
                customFields);
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || !value.isValueNode()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    /** Flattens a JIRA field value (a string, number, {@code {value: ...}}, {@code {name: ...}}, a user picker, a
     * cascading select or an array) into the text shown in the review table. */
    private static String scalar(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return clean(node.asText());
        }
        if (node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        if (node.isArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode element : node) {
                String part = scalar(element);
                if (part != null && !parts.contains(part)) {
                    parts.add(part);
                }
            }
            return parts.isEmpty() ? null : String.join(", ", parts);
        }
        if (node.isObject()) {
            // People fields (CM PM, CM SA, ADM PM, ADM SA) carry the person in displayName; select/option fields in
            // value; project-like objects in name/key.
            for (String label : new String[] {"value", "displayName", "name", "key"}) {
                if (node.hasNonNull(label)) {
                    String resolved = scalar(node.get(label));
                    if (resolved != null) {
                        return resolved;
                    }
                }
            }
            // Cascading selects nest the answer one level down.
            for (String child : new String[] {"children", "child"}) {
                if (node.hasNonNull(child)) {
                    String resolved = scalar(node.get(child));
                    if (resolved != null) {
                        return resolved;
                    }
                }
            }
            // Never let a raw JSON blob reach a table cell.
            log.debug("Unrecognised JIRA field shape, value left blank: {}", node);
            return null;
        }
        return null;
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String collapsed = value.trim().replaceAll("\\s+", " ");
        return collapsed.isEmpty() ? null : collapsed;
    }
}

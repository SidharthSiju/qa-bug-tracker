package com.duc.crqa.notification.service;

import com.duc.crqa.mail.entity.MailConfig;
import com.duc.crqa.mail.service.MailConfigService;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Sends notification email by calling the corporate SOAP email adapter: the configured request template is filled in
 * and POSTed to the configured endpoint, and the adapter (or an SMTP relay behind it) does the delivery.
 *
 * <p>Placeholders: {@code {{fromMailFromConfiguration}}}, {@code {{toMailDynamic}}}, {@code {{subjectDynamic}}},
 * {@code {{htmlBodyDynamic}}} (inserted verbatim — the shipped template wraps it in CDATA), the SOAP credentials
 * {@code {{username}}} / {@code {{password}}}, plus any {@code name=value} pairs from the SOAP variables. Values going
 * into XML are escaped, and an unresolved placeholder is reported rather than sent as {@code {{name}}}.
 */
@Component
public class SoapEmailNotifier implements EmailNotifier {

    private static final Logger log = LoggerFactory.getLogger(SoapEmailNotifier.class);

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*}}");
    private static final Pattern FAULT_STRING = Pattern.compile("<faultstring[^>]*>(.*?)</faultstring>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    /** Inserted as-is (already HTML): the template already wraps it in CDATA. */
    private static final String HTML_BODY_PLACEHOLDER = "htmlBodyDynamic";

    private final MailConfigService mailConfigService;

    public SoapEmailNotifier(MailConfigService mailConfigService) {
        this.mailConfigService = mailConfigService;
    }

    @Override
    @Async("notificationExecutor")
    public void send(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            return;
        }
        String reason = mailConfigService.unavailableReason();
        if (reason != null) {
            log.debug("Notification email to {} skipped: {}", to, reason);
            return;
        }
        try {
            deliver(to, subject, body);
        } catch (Exception ex) {
            log.warn("Could not email the notification to {} through the SOAP adapter: {}", to, ex.getMessage());
        }
    }

    @Override
    public void sendNow(String to, String subject, String body) {
        String reason = mailConfigService.unavailableReason();
        if (reason != null) {
            throw new IllegalStateException(reason);
        }
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("Enter an address to send the test email to");
        }
        deliver(to, subject, body);
    }

    private void deliver(String to, String subject, String body) {
        MailConfig config = mailConfigService.current();
        Map<String, String> values = placeholderValues(config, to, subject, body);

        String endpoint = substitute(config.getSoapEndpoint(), values, false);
        String payload = substitute(config.getSoapPayload(), values, true);

        if (endpoint.isBlank()) {
            throw new IllegalStateException("No SOAP endpoint is configured");
        }
        String response = post(endpoint, payload, config.getTimeoutSeconds());
        log.info("Notification email handed to the SOAP adapter for {} ({})", to, subject);
        log.debug("SOAP adapter response for {}: {}", to, snippet(response));
    }

    private Map<String, String> placeholderValues(MailConfig config, String to, String subject, String body) {
        Map<String, String> values = new LinkedHashMap<>(mailConfigService.soapVariables());
        // Built-in values win over an administrator variable of the same name.
        values.put(HTML_BODY_PLACEHOLDER, toHtml(body));
        values.put("fromMailFromConfiguration", nullToEmpty(config.getFromAddress()));
        values.put("toMailDynamic", nullToEmpty(to));
        values.put("subjectDynamic", nullToEmpty(subject));
        values.put("username", nullToEmpty(config.getSoapUsername()));
        values.put("password", nullToEmpty(config.getSoapPassword()));
        return values;
    }

    /** Replaces every {@code {{name}}}; values are XML-escaped for the payload and left raw in the URL. */
    private String substitute(String template, Map<String, String> values, boolean xmlContext) {
        if (template == null) {
            return "";
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!values.containsKey(name)) {
                throw new IllegalStateException("The SOAP " + (xmlContext ? "request template" : "endpoint")
                        + " uses {{" + name + "}} but no value is configured for it"
                        + " (add it to the SOAP variables)");
            }
            String value = nullToEmpty(values.get(name));
            String replacement;
            if (xmlContext && HTML_BODY_PLACEHOLDER.equals(name)) {
                replacement = cdataSafe(value);
            } else {
                replacement = xmlContext ? xml(value) : value.replaceAll("\\s+", "");
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String post(String endpoint, String payload, int timeoutSeconds) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        RestClient client = RestClient.builder().requestFactory(requestFactory).build();

        try {
            // The response is read as bytes and decoded here: the adapter does not always label its response usefully,
            // and a SOAP fault (often sent as HTTP 500) has to reach the caller as text so it can be reported.
            return client.post()
                    .uri(URI.create(endpoint))
                    .contentType(MediaType.TEXT_XML)
                    .accept(MediaType.TEXT_XML, MediaType.APPLICATION_XML, MediaType.ALL)
                    .body(payload)
                    .exchange((request, response) -> {
                        String body = response.getBody() == null
                                ? ""
                                : new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        // A fault is reported as itself, whether it arrives with HTTP 200 or 500.
                        String fault = faultString(body);
                        if (fault != null) {
                            throw new IllegalStateException("The SOAP adapter reported a fault: " + fault);
                        }
                        if (response.getStatusCode().isError()) {
                            throw new IllegalStateException("The SOAP endpoint returned "
                                    + response.getStatusCode().value() + ": " + snippet(body));
                        }
                        return body;
                    });
        } catch (ResourceAccessException ex) {
            throw new IllegalStateException("Could not reach the SOAP endpoint " + endpoint + ": " + ex.getMessage());
        } catch (RestClientException ex) {
            throw new IllegalStateException("The SOAP call failed for " + endpoint + ": " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("The SOAP endpoint is not a valid URL: " + endpoint);
        }
    }

    /** The {@code faultstring} of a SOAP fault in the response, or {@code null} when there is no fault. */
    private static String faultString(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }
        String lower = response.toLowerCase(Locale.ROOT);
        if (!lower.contains("fault")) {
            return null;
        }
        Matcher matcher = FAULT_STRING.matcher(response);
        return matcher.find() ? matcher.group(1).trim() : snippet(response);
    }

    private static String xml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /** CDATA cannot contain {@code ]]>} — split it so the payload stays valid XML. */
    private static String cdataSafe(String value) {
        return value.replace("]]>", "]]]]><![CDATA[>");
    }

    /** Escapes the message and turns line breaks into {@code <br>}, because the template expects HTML. */
    private static String toHtml(String body) {
        return xml(nullToEmpty(body)).replace("\r\n", "\n").replace("\n", "<br>\n");
    }

    private static String snippet(String value) {
        if (value == null) {
            return "(no response body)";
        }
        String collapsed = value.replaceAll("\\s+", " ").trim();
        return collapsed.length() > 300 ? collapsed.substring(0, 300) + "..." : collapsed;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

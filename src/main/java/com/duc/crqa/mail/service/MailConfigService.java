package com.duc.crqa.mail.service;

import com.duc.crqa.common.enums.MailTransport;
import com.duc.crqa.config.MailProperties;
import com.duc.crqa.mail.dto.MailSettingsForm;
import com.duc.crqa.mail.entity.MailConfig;
import com.duc.crqa.mail.repository.MailConfigRepository;
import com.duc.crqa.security.CurrentUserService;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Holds the single global notification-email settings row (id = 1), seeded from {@code app.mail.*} the first time it
 * is used. Values are normalised on read and write, and a blank stored value falls back to the configured default.
 */
@Service
public class MailConfigService {

    public static final Long SETTINGS_ID = 1L;
    public static final int MAX_TIMEOUT_SECONDS = 300;

    private final MailConfigRepository mailConfigRepository;
    private final MailProperties mailProperties;
    private final CurrentUserService currentUserService;

    private volatile MailConfig cached;

    public MailConfigService(MailConfigRepository mailConfigRepository,
                             MailProperties mailProperties,
                             CurrentUserService currentUserService) {
        this.mailConfigRepository = mailConfigRepository;
        this.mailProperties = mailProperties;
        this.currentUserService = currentUserService;
    }

    public MailConfig current() {
        MailConfig local = cached;
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
    public void update(MailSettingsForm form) {
        MailConfig config = mailConfigRepository.findById(SETTINGS_ID).orElseGet(() -> {
            MailConfig created = new MailConfig();
            created.setId(SETTINGS_ID);
            return created;
        });
        config.setEnabled(form.isEnabled());
        config.setTransport(form.getTransport() == null ? MailTransport.SMTP : form.getTransport());
        config.setHost(normalizeText(form.getHost(), mailProperties.getHost()));
        config.setPort(normalizePort(form.getPort()));
        config.setUsername(normalizeText(form.getUsername(), mailProperties.getUsername()));
        config.setPassword(normalizeText(form.getPassword(), mailProperties.getPassword()));
        config.setFromAddress(normalizeText(form.getFromAddress(), mailProperties.getFromAddress()));
        config.setStartTls(form.isStartTls());
        config.setTimeoutSeconds(normalizeTimeout(form.getTimeoutSeconds()));
        config.setSoapEndpoint(normalizeText(form.getSoapEndpoint(), mailProperties.getSoapEndpoint()));
        config.setSoapUsername(normalizeText(form.getSoapUsername(), mailProperties.getSoapUsername()));
        config.setSoapPassword(normalizeText(form.getSoapPassword(), mailProperties.getSoapPassword()));
        config.setSoapPayload(normalizeText(form.getSoapPayload(), mailProperties.getSoapPayload()));
        config.setSoapVariables(normalizeText(form.getSoapVariables(), mailProperties.getSoapVariables()));
        config.setUpdatedAt(LocalDateTime.now());
        config.setUpdatedBy(currentUserService.currentUserId());
        cached = mailConfigRepository.save(config);
    }

    public MailTransport transport() {
        return current().getTransport();
    }

    /** True when the SOAP adapter is the selected transport (otherwise the SMTP relay is used). */
    public boolean isSoap() {
        return transport() == MailTransport.SOAP;
    }

    public String soapEndpoint() {
        return current().getSoapEndpoint();
    }

    public String soapUsername() {
        return current().getSoapUsername();
    }

    public String soapPassword() {
        return current().getSoapPassword();
    }

    public String soapPayload() {
        return current().getSoapPayload();
    }

    /**
     * The administrator-defined placeholder values for the SOAP endpoint and payload, parsed from
     * {@code name=value} lines (blank lines and {@code #} comments are ignored).
     */
    public Map<String, String> soapVariables() {
        Map<String, String> variables = new LinkedHashMap<>();
        String raw = current().getSoapVariables();
        if (raw == null) {
            return variables;
        }
        for (String line : raw.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int separator = trimmed.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            variables.put(trimmed.substring(0, separator).trim(), trimmed.substring(separator + 1).trim());
        }
        return variables;
    }

    /** True when email may actually be sent with the selected transport. */
    public boolean isReady() {
        return unavailableReason() == null;
    }

    /** Explains why email cannot be sent with the selected transport, or {@code null} when it can. */
    public String unavailableReason() {
        MailConfig config = current();
        if (!config.isEnabled()) {
            return "Email sending is switched off";
        }
        if (isBlank(config.getFromAddress())) {
            return "No from address is configured";
        }
        if (config.getTransport() == MailTransport.SOAP) {
            if (isBlank(config.getSoapEndpoint())) {
                return "No SOAP endpoint is configured";
            }
            if (isBlank(config.getSoapPayload())) {
                return "No SOAP request template is configured";
            }
            return null;
        }
        if (isBlank(config.getHost())) {
            return "No SMTP host is configured";
        }
        return null;
    }

    private MailConfig load() {
        MailConfig config = mailConfigRepository.findById(SETTINGS_ID).orElse(null);
        if (config == null) {
            MailConfig created = new MailConfig();
            created.setId(SETTINGS_ID);
            created.setEnabled(mailProperties.isEnabled());
            created.setTransport(mailProperties.getTransport());
            created.setHost(normalizeText(mailProperties.getHost(), null));
            created.setPort(normalizePort(mailProperties.getPort()));
            created.setUsername(normalizeText(mailProperties.getUsername(), null));
            created.setPassword(normalizeText(mailProperties.getPassword(), null));
            created.setFromAddress(normalizeText(mailProperties.getFromAddress(), null));
            created.setStartTls(mailProperties.isStartTls());
            created.setTimeoutSeconds(normalizeTimeout(mailProperties.getTimeoutSeconds()));
            created.setSoapEndpoint(normalizeText(mailProperties.getSoapEndpoint(), null));
            created.setSoapUsername(normalizeText(mailProperties.getSoapUsername(), null));
            created.setSoapPassword(normalizeText(mailProperties.getSoapPassword(), null));
            created.setSoapPayload(mailProperties.getSoapPayload());
            created.setSoapVariables(normalizeText(mailProperties.getSoapVariables(), null));
            return mailConfigRepository.save(created);
        }
        // A blank stored value falls back to the configured default; the switches themselves are what is stored.
        if (config.getTransport() == null) {
            config.setTransport(mailProperties.getTransport());
        }
        config.setHost(normalizeText(config.getHost(), mailProperties.getHost()));
        config.setUsername(normalizeText(config.getUsername(), mailProperties.getUsername()));
        config.setPassword(normalizeText(config.getPassword(), mailProperties.getPassword()));
        config.setFromAddress(normalizeText(config.getFromAddress(), mailProperties.getFromAddress()));
        config.setPort(normalizePort(config.getPort()));
        config.setTimeoutSeconds(normalizeTimeout(config.getTimeoutSeconds()));
        config.setSoapEndpoint(normalizeText(config.getSoapEndpoint(), mailProperties.getSoapEndpoint()));
        config.setSoapUsername(normalizeText(config.getSoapUsername(), mailProperties.getSoapUsername()));
        config.setSoapPassword(normalizeText(config.getSoapPassword(), mailProperties.getSoapPassword()));
        config.setSoapPayload(normalizeText(config.getSoapPayload(), mailProperties.getSoapPayload()));
        config.setSoapVariables(normalizeText(config.getSoapVariables(), mailProperties.getSoapVariables()));
        return mailConfigRepository.save(config);
    }

    private int normalizePort(Integer value) {
        if (value == null || value < 1 || value > 65535) {
            int fallback = mailProperties.getPort();
            return fallback < 1 || fallback > 65535 ? 25 : fallback;
        }
        return value;
    }

    private int normalizeTimeout(Integer value) {
        int effective = value == null || value < 1 ? mailProperties.getTimeoutSeconds() : value;
        return Math.max(1, Math.min(MAX_TIMEOUT_SECONDS, effective));
    }

    private static String normalizeText(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

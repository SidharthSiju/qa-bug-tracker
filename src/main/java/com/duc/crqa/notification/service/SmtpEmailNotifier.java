package com.duc.crqa.notification.service;

import com.duc.crqa.mail.entity.MailConfig;
import com.duc.crqa.mail.service.MailConfigService;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Sends notification email through the SMTP relay configured under Administration -> Email settings.
 *
 * <p>Nothing is sent (and nothing fails) when email is switched off or the relay is not configured — the in-app
 * notification is always the source of truth. The relay is contacted off the request thread with a bounded timeout, so
 * a slow or dead relay can never hang or break a workflow action.
 */
@Component
public class SmtpEmailNotifier implements EmailNotifier {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailNotifier.class);

    private final MailConfigService mailConfigService;

    public SmtpEmailNotifier(MailConfigService mailConfigService) {
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
            // Debug level: with email switched off this happens for every notification.
            log.debug("Notification email to {} skipped: {}", to, reason);
            return;
        }
        try {
            deliver(to, subject, body);
        } catch (Exception ex) {
            log.warn("Could not email the notification to {}: {}", to, ex.getMessage());
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
        JavaMailSenderImpl sender = mailSender(config);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(config.getFromAddress());
        message.setTo(to);
        message.setSubject(subject == null ? "CR Issue Tracker notification" : subject);
        message.setText(body == null ? "" : body);
        sender.send(message);

        log.info("Notification email sent to {} ({})", to, subject);
    }

    private JavaMailSenderImpl mailSender(MailConfig config) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.getHost());
        sender.setPort(config.getPort());
        if (config.getUsername() != null && !config.getUsername().isBlank()) {
            sender.setUsername(config.getUsername());
            sender.setPassword(config.getPassword());
            // JavaMail only attempts AUTH when asked to, so relays that require credentials need this.
            sender.getJavaMailProperties().put("mail.smtp.auth", "true");
        }
        Duration timeout = Duration.ofSeconds(Math.max(1, config.getTimeoutSeconds()));
        sender.getJavaMailProperties().put("mail.smtp.connectiontimeout", String.valueOf(timeout.toMillis()));
        sender.getJavaMailProperties().put("mail.smtp.timeout", String.valueOf(timeout.toMillis()));
        sender.getJavaMailProperties().put("mail.smtp.writetimeout", String.valueOf(timeout.toMillis()));
        if (config.isStartTls()) {
            sender.getJavaMailProperties().put("mail.smtp.starttls.enable", "true");
        }
        return sender;
    }
}

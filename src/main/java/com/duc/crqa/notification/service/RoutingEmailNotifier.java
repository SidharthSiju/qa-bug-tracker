package com.duc.crqa.notification.service;

import com.duc.crqa.mail.service.MailConfigService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * The notifier the application actually uses: hands each message to the transport selected on the Email settings
 * screen (SMTP relay or the SOAP email adapter). The choice is read per message, so switching transport takes effect
 * immediately — no restart.
 *
 * <p>The transports are injected by their {@link EmailNotifier} interface with an explicit qualifier: {@code @Async}
 * makes Spring proxy them by interface (a JDK proxy has no concrete-type bean), so the concrete classes cannot be
 * injected directly.
 */
@Component
@Primary
public class RoutingEmailNotifier implements EmailNotifier {

    private final MailConfigService mailConfigService;
    private final EmailNotifier smtpEmailNotifier;
    private final EmailNotifier soapEmailNotifier;

    public RoutingEmailNotifier(MailConfigService mailConfigService,
                                @Qualifier("smtpEmailNotifier") EmailNotifier smtpEmailNotifier,
                                @Qualifier("soapEmailNotifier") EmailNotifier soapEmailNotifier) {
        this.mailConfigService = mailConfigService;
        this.smtpEmailNotifier = smtpEmailNotifier;
        this.soapEmailNotifier = soapEmailNotifier;
    }

    @Override
    public void send(String to, String subject, String body) {
        delegate().send(to, subject, body);
    }

    @Override
    public void sendNow(String to, String subject, String body) {
        delegate().sendNow(to, subject, body);
    }

    private EmailNotifier delegate() {
        return mailConfigService.isSoap() ? soapEmailNotifier : smtpEmailNotifier;
    }
}

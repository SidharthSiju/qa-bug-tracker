package com.duc.crqa.config;

import com.duc.crqa.common.enums.MailTransport;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Defaults for the notification email, bound to {@code app.mail.*} in application.yml. These seed the editable
 * {@code IT_mail_config} row the first time the Email settings screen is used; from then on the stored values win.
 */
@Component
@ConfigurationProperties(prefix = "app.mail")
@Getter
@Setter
public class MailProperties {

    /**
     * Shipped request template for the corporate SOAP email adapter.
     *
     * <p>Placeholders: {@code {{fromMailFromConfiguration}}}, {@code {{toMailDynamic}}}, {@code {{subjectDynamic}}},
     * {@code {{htmlBodyDynamic}}} (inserted verbatim — the template wraps it in CDATA), the SOAP credentials
     * {@code {{username}}} / {@code {{password}}}, and any other {@code {{name}}} defined in {@link #soapVariables}.
     */
    private static final String DEFAULT_SOAP_PAYLOAD = """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                <soap:Header xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                    <wataniya:auth xmlns:wataniya="http://osb.wataniya.com">
                        <username>{{username}}</username>
                        <password>{{password}}</password>
                    </wataniya:auth>
                </soap:Header>
                <soapenv:Body>
                    <ema:emailWithAttachment xmlns:ema="http://osb.wataniya.com/EmailWithAttachmentService">
                        <aFromEmailAddr>{{fromMailFromConfiguration}}</aFromEmailAddr>
                        <aToEmailAddr>{{toMailDynamic}}</aToEmailAddr>
                        <aSubject>{{subjectDynamic}}</aSubject>
                        <message><![CDATA[{{htmlBodyDynamic}}
                        ]]></message>
                        <emailAttachment></emailAttachment>
                        <attachmentName></attachmentName>
                        <aCcEmailAddr/>
                        <aBccEmailAddr/>
                    </ema:emailWithAttachment>
                </soapenv:Body>
            </soapenv:Envelope>
            """;

    /** Whether notifications are emailed. Off by default: nothing is sent until a transport is configured. */
    private boolean enabled = false;

    /** How email leaves the application: an SMTP relay, or the corporate SOAP email adapter. */
    private MailTransport transport = MailTransport.SMTP;

    /** SMTP relay host (an intranet relay — never an internet service). */
    private String host = "";

    private int port = 25;

    private String username = "";

    private String password = "";

    /** From address used on outgoing notification mail (also {@code {{fromMailFromConfiguration}}}). */
    private String fromAddress = "";

    /** Enable STARTTLS (usually required on port 587). */
    private boolean startTls = false;

    /** Connect/read timeout in seconds, so a dead relay or adapter can never hang a request. */
    private int timeoutSeconds = 20;

    /** SOAP transport: URL template; every {@code {{placeholder}}} is resolved from {@link #soapVariables}. */
    private String soapEndpoint = "{{sit_osb}}/EMAILAdapter/ProxyService/EmailWithAttachmentProxy";

    /** SOAP transport: username for the {@code wataniya:auth} header in the payload. */
    private String soapUsername = "";

    /** SOAP transport: password for the {@code wataniya:auth} header in the payload. */
    private String soapPassword = "";

    /** SOAP transport: extra placeholder values, one {@code name=value} per line. */
    private String soapVariables = "sit_osb=";

    /** SOAP transport: the request template (see {@link #DEFAULT_SOAP_PAYLOAD}). */
    private String soapPayload = DEFAULT_SOAP_PAYLOAD;
}

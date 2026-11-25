package com.duc.crqa.mail.entity;

import com.duc.crqa.common.entity.IdEntity;
import com.duc.crqa.common.enums.MailTransport;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Singleton row (id = 1) holding the SMTP relay settings used to email notifications, editable by an ADMIN.
 */
@Entity
@Table(name = "IT_mail_config")
@Getter
@Setter
@NoArgsConstructor
public class MailConfig extends IdEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(nullable = false)
    private boolean enabled = false;

    /** SMTP or SOAP: how the notification email is handed to the mail infrastructure. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MailTransport transport = MailTransport.SMTP;

    @Column(length = 255)
    private String host;

    @Column(nullable = false)
    private int port;

    @Column(length = 255)
    private String username;

    @Column(length = 255)
    private String password;

    @Column(name = "from_address", length = 255)
    private String fromAddress;

    @Column(name = "start_tls", nullable = false)
    private boolean startTls;

    @Column(name = "timeout_seconds", nullable = false)
    private int timeoutSeconds;

    /** SOAP transport: URL template, e.g. {@code {{sit_osb}}/EMAILAdapter/ProxyService/EmailWithAttachmentProxy}. */
    @Column(name = "soap_endpoint", length = 1000)
    private String soapEndpoint;

    @Column(name = "soap_username", length = 255)
    private String soapUsername;

    @Column(name = "soap_password", length = 255)
    private String soapPassword;

    /** SOAP transport: the request XML template with {@code {{placeholders}}}. */
    @Column(name = "soap_payload", length = 4000)
    private String soapPayload;

    /** SOAP transport: extra placeholder values, one {@code name=value} per line (e.g. {@code sit_osb=https://...}). */
    @Column(name = "soap_variables", length = 2000)
    private String soapVariables;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;
}

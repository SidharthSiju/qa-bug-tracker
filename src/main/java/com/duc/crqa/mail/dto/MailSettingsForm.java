package com.duc.crqa.mail.dto;

import com.duc.crqa.common.enums.MailTransport;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Everything an administrator can change on the Email settings screen. */
@Getter
@Setter
@NoArgsConstructor
public class MailSettingsForm {

    private boolean enabled;

    private MailTransport transport = MailTransport.SMTP;

    private String host;

    private Integer port;

    private String username;

    private String password;

    private String fromAddress;

    private boolean startTls;

    private Integer timeoutSeconds;

    private String soapEndpoint;

    private String soapUsername;

    private String soapPassword;

    private String soapPayload;

    private String soapVariables;
}

package com.duc.crqa.common.enums;

/**
 * How notification email leaves the application: through an SMTP relay, or by calling the corporate SOAP email adapter.
 */
public enum MailTransport {

    SMTP("SMTP relay"),
    SOAP("SOAP API");

    private final String label;

    MailTransport(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

package com.duc.crqa.jira.exception;

/**
 * Raised when JIRA cannot be reached or returns an unusable response. The message is shown to the user, so it must
 * always be human readable and must never leak credentials.
 */
public class JiraException extends RuntimeException {

    public JiraException(String message) {
        super(message);
    }

    public JiraException(String message, Throwable cause) {
        super(message, cause);
    }
}

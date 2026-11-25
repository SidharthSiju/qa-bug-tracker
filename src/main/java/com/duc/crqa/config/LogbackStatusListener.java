package com.duc.crqa.config;

import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.status.StatusListener;

/**
 * Prints warnings and errors from Logback itself to standard error.
 *
 * <p>Logback disables an appender that cannot be started (an unwritable log directory, for example) and says nothing
 * about it by default: the application keeps running with console logging only, which looks exactly like "the
 * application log file is not generated". With this listener the reason appears on stderr — and therefore in the
 * systemd journal — as soon as the problem happens.
 */
public class LogbackStatusListener implements StatusListener {

    @Override
    public void addStatusEvent(Status status) {
        if (status.getLevel() < Status.WARN) {
            return;
        }
        System.err.println("[logging] " + levelName(status.getLevel()) + " in " + status.getOrigin() + ": "
                + status.getMessage()
                + (status.getThrowable() != null ? " (" + status.getThrowable() + ")" : ""));
    }

    private static String levelName(int level) {
        return switch (level) {
            case Status.ERROR -> "ERROR";
            case Status.WARN -> "WARN";
            case Status.INFO -> "INFO";
            default -> "DEBUG";
        };
    }
}

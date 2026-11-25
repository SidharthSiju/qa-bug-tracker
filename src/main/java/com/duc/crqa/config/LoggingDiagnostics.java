package com.duc.crqa.config;

import ch.qos.logback.classic.LoggerContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reports, once at start-up, where the application log file is written — and warns with the fix when it cannot be.
 *
 * <p>This exists because the log file path is resolved from the working directory by default: a service started by
 * systemd runs with a working directory of {@code /}, so a relative {@code ../logs} lands somewhere unusable and the
 * file appender quietly does nothing. The line below states the resolved file and its writability, so a deployment
 * that produces no log file says why.
 */
@Component
public class LoggingDiagnostics {

    private static final Logger log = LoggerFactory.getLogger(LoggingDiagnostics.class);

    @EventListener(ApplicationReadyEvent.class)
    public void reportLogFile() {
        String configured = logbackProperty("LOG_FILE");
        if (configured == null) {
            log.info("Application log file is not configured; logs go to the console only");
            return;
        }
        Path logFile = Paths.get(configured + ".log").toAbsolutePath().normalize();
        Path directory = logFile.getParent();
        if (directory != null && isWritable(directory)) {
            log.info("Application log file: {}", logFile);
        } else {
            log.warn("Application log file could not be written: {} ({} is not writable by {}) - logs go to the console "
                            + "only; set LOG_PATH (or -DLOG_PATH) to a directory this user can write, for example "
                            + "/var/log/cr-qa-management", logFile, directory, System.getProperty("user.name"));
        }
    }

    private static boolean isWritable(Path directory) {
        try {
            Files.createDirectories(directory);
            return Files.isWritable(directory);
        } catch (Exception ex) {
            return false;
        }
    }

    /** Reads a value Logback put in its context (Spring Boot maps {@code logging.file.path} to {@code LOG_PATH}). */
    private static String logbackProperty(String name) {
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            String value = context.getProperty(name);
            return value == null || value.isBlank() ? null : value;
        }
        return null;
    }
}

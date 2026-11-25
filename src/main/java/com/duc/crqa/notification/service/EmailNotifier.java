package com.duc.crqa.notification.service;

/**
 * Delivers notification email. The implementation sends over the configured intranet SMTP relay; when email is
 * switched off or not configured it does nothing, so notifications stay in-app only.
 */
public interface EmailNotifier {

    /**
     * Queues a notification email. Runs off the request thread and never throws: a relay that is down must not slow
     * down or roll back the action that produced the notification.
     */
    void send(String to, String subject, String body);

    /**
     * Sends immediately and reports failures to the caller, so an administrator can see exactly what the relay said
     * (used by the "Send test email" action on the Email settings screen).
     *
     * @throws IllegalStateException    when email is switched off or not configured
     * @throws IllegalArgumentException when no recipient was given
     */
    void sendNow(String to, String subject, String body);
}

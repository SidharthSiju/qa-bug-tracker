package com.duc.crqa.config;

import com.duc.crqa.audit.service.AuditService;
import com.duc.crqa.security.CustomUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationLoggingListener {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationLoggingListener.class);

    private final AuditService auditService;

    public AuthenticationLoggingListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @EventListener
    public void onLoginSuccess(InteractiveAuthenticationSuccessEvent event) {
        Authentication authentication = event.getAuthentication();
        String username = authentication == null ? "unknown" : authentication.getName();
        Long userId = authentication != null
                && authentication.getPrincipal() instanceof CustomUserDetails details ? details.getUserId() : null;

        MDC.put("username", username);
        MDC.put("userId", userId == null ? "" : String.valueOf(userId));
        log.info("User logged in");
        MDC.clear();

        auditService.record(username, "LOGIN", "USER", userId, null, null);
    }

    @EventListener
    public void onLogoutSuccess(LogoutSuccessEvent event) {
        Authentication authentication = event.getAuthentication();
        String username = authentication == null ? "unknown" : authentication.getName();
        Long userId = authentication != null
                && authentication.getPrincipal() instanceof CustomUserDetails details ? details.getUserId() : null;

        MDC.put("username", username);
        MDC.put("userId", "");
        log.info("User logged out");
        MDC.clear();

        auditService.record(username, "LOGOUT", "USER", userId, null, null);
    }
}

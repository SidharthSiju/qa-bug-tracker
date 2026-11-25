package com.duc.crqa.config;

import com.duc.crqa.audit.service.AuditService;
import com.duc.crqa.security.CustomUserDetails;
import org.springframework.context.ApplicationListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.switchuser.AuthenticationSwitchUserEvent;
import org.springframework.security.web.authentication.switchuser.SwitchUserGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class ImpersonationAuditListener implements ApplicationListener<AuthenticationSwitchUserEvent> {

    private final AuditService auditService;

    public ImpersonationAuditListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void onApplicationEvent(AuthenticationSwitchUserEvent event) {
        Authentication authentication = event.getAuthentication();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (authority instanceof SwitchUserGrantedAuthority switchAuthority) {
                String admin = usernameOf(switchAuthority.getSource());
                auditService.record(admin, "USER_IMPERSONATION_STARTED", "USER",
                        userIdOf(event.getTargetUser()), admin, usernameOf(event.getTargetUser()));
                return;
            }
        }
        String admin = usernameOf(authentication);
        Long adminId = authentication.getPrincipal() instanceof CustomUserDetails details
                ? details.getUserId() : null;
        auditService.record(admin, "USER_IMPERSONATION_ENDED", "USER", adminId, null, admin);
    }

    private String usernameOf(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        if (authentication.getPrincipal() instanceof CustomUserDetails details) {
            return details.getUsername();
        }
        return authentication.getName();
    }

    private String usernameOf(UserDetails userDetails) {
        return userDetails == null ? null : userDetails.getUsername();
    }

    private Long userIdOf(UserDetails userDetails) {
        return userDetails instanceof CustomUserDetails details ? details.getUserId() : null;
    }
}

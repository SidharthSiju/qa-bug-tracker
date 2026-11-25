package com.duc.crqa.security;

import com.duc.crqa.common.enums.Role;
import com.duc.crqa.user.entity.User;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.switchuser.SwitchUserGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserService {

    public CustomUserDetails currentUserDetails() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getPrincipal() instanceof CustomUserDetails details) {
            return details;
        }
        return null;
    }

    public User currentUser() {
        CustomUserDetails details = currentUserDetails();
        return details == null ? null : details.getUser();
    }

    public Long currentUserId() {
        User user = currentUser();
        return user == null ? null : user.getId();
    }

    public String currentUsername() {
        User user = currentUser();
        return user == null ? null : user.getUsername();
    }

    public boolean hasRole(Role role) {
        User user = currentUser();
        return user != null && user.hasRole(role);
    }

    public boolean hasAnyRole(Role... roles) {
        User user = currentUser();
        if (user == null) {
            return false;
        }
        for (Role role : roles) {
            if (user.hasRole(role)) {
                return true;
            }
        }
        return false;
    }

    public boolean isImpersonating() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority instanceof SwitchUserGrantedAuthority);
    }

    public User impersonator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (authority instanceof SwitchUserGrantedAuthority switchAuthority) {
                Authentication source = switchAuthority.getSource();
                if (source != null && source.getPrincipal() instanceof CustomUserDetails details) {
                    return details.getUser();
                }
            }
        }
        return null;
    }
}

package com.duc.crqa.controller;

import com.duc.crqa.admin.service.UiSettingsService;
import com.duc.crqa.common.enums.Role;
import com.duc.crqa.jira.service.JiraConfigService;
import com.duc.crqa.notification.repository.NotificationRepository;
import com.duc.crqa.security.CurrentUserService;
import com.duc.crqa.security.Permissions;
import com.duc.crqa.user.entity.User;
import com.duc.crqa.user.service.UserService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAdvice {

    private final CurrentUserService currentUserService;
    private final NotificationRepository notificationRepository;
    private final UserService userService;
    private final UiSettingsService uiSettingsService;
    private final JiraConfigService jiraConfigService;

    public GlobalModelAdvice(CurrentUserService currentUserService,
                             NotificationRepository notificationRepository,
                             UserService userService,
                             UiSettingsService uiSettingsService,
                             JiraConfigService jiraConfigService) {
        this.currentUserService = currentUserService;
        this.notificationRepository = notificationRepository;
        this.userService = userService;
        this.uiSettingsService = uiSettingsService;
        this.jiraConfigService = jiraConfigService;
    }

    /** Drives the visibility of the "Import from JIRA" button (Administration -> JIRA Configuration). */
    @ModelAttribute("jiraEnabled")
    public boolean jiraEnabled() {
        return jiraConfigService.isEnabled();
    }

    @ModelAttribute("appTheme")
    public String appTheme() {
        return uiSettingsService.current().getTheme();
    }

    @ModelAttribute("appFont")
    public String appFont() {
        return uiSettingsService.current().getFont();
    }

    @ModelAttribute("appFontSize")
    public int appFontSize() {
        return uiSettingsService.current().getFontSize();
    }

    @ModelAttribute("currentUser")
    public User currentUser() {
        return currentUserService.currentUser();
    }

    @ModelAttribute("unreadNotifications")
    public long unreadNotifications() {
        User user = currentUserService.currentUser();
        return user == null ? 0 : notificationRepository.countByRecipientIdAndReadFlagFalse(user.getId());
    }

    @ModelAttribute("userMap")
    public Map<Long, User> userMap() {
        return userService.listAll().stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    @ModelAttribute("canEditChangeRequests")
    public boolean canEditChangeRequests() {
        return Permissions.canEditChangeRequests(currentRoles());
    }

    @ModelAttribute("canEditBugs")
    public boolean canEditBugs() {
        return Permissions.canEditBugs(currentRoles());
    }

    @ModelAttribute("canEditTestCycles")
    public boolean canEditTestCycles() {
        return Permissions.canEditTestCycles(currentRoles());
    }

    @ModelAttribute("canEditTestCases")
    public boolean canEditTestCases() {
        return Permissions.canEditTestCases(currentRoles());
    }

    @ModelAttribute("canManageUsers")
    public boolean canManageUsers() {
        return Permissions.canManageUsers(currentRoles());
    }

    @ModelAttribute("canViewQaStats")
    public boolean canViewQaStats() {
        return Permissions.canViewQaStats(currentRoles());
    }

    @ModelAttribute("isAdmin")
    public boolean isAdmin() {
        return Permissions.isAdmin(currentRoles());
    }

    @ModelAttribute("canDeleteTestCases")
    public boolean canDeleteTestCases() {
        return Permissions.canDeleteArtifacts(currentRoles());
    }

    @ModelAttribute("canDeleteBugs")
    public boolean canDeleteBugs() {
        return Permissions.canDeleteArtifacts(currentRoles());
    }

    @ModelAttribute("canDeleteChangeRequests")
    public boolean canDeleteChangeRequests() {
        return Permissions.canDeleteChangeRequests(currentRoles());
    }

    @ModelAttribute("canDeleteTestCycles")
    public boolean canDeleteTestCycles() {
        return Permissions.canDeleteArtifacts(currentRoles());
    }

    @ModelAttribute("isImpersonating")
    public boolean isImpersonating() {
        return currentUserService.isImpersonating();
    }

    @ModelAttribute("impersonatorLabel")
    public String impersonatorLabel() {
        User impersonator = currentUserService.impersonator();
        return impersonator == null ? null : impersonator.displayLabel();
    }

    @ModelAttribute("impersonateUsers")
    public List<User> impersonateUsers() {
        Long currentId = currentUserService.currentUserId();
        return userService.listActive().stream()
                .filter(user -> !user.getId().equals(currentId))
                .toList();
    }

    private Set<Role> currentRoles() {
        User user = currentUserService.currentUser();
        return user == null ? Set.of() : user.getRoles();
    }
}

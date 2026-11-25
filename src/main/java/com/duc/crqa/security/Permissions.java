package com.duc.crqa.security;

import com.duc.crqa.common.enums.Role;
import java.util.EnumSet;
import java.util.Set;

public final class Permissions {

    private static final Set<Role> CHANGE_REQUEST_EDIT =
            EnumSet.of(Role.ADMIN, Role.CR_MANAGER, Role.SA_LEAD, Role.QA_LEAD);
    private static final Set<Role> BUG_EDIT =
            EnumSet.of(Role.ADMIN, Role.SA_LEAD, Role.QA_LEAD, Role.QA_TESTER, Role.DEVELOPER);
    private static final Set<Role> TEST_CYCLE_EDIT =
            EnumSet.of(Role.ADMIN, Role.QA_LEAD, Role.QA_TESTER, Role.DEVELOPER);
    private static final Set<Role> TEST_CASE_EDIT =
            EnumSet.of(Role.ADMIN, Role.SA_LEAD, Role.QA_LEAD, Role.QA_TESTER, Role.DEVELOPER);
    private static final Set<Role> USER_MANAGE =
            EnumSet.of(Role.ADMIN, Role.QA_LEAD);
    private static final Set<Role> QA_STATS_VIEW =
            EnumSet.of(Role.ADMIN, Role.CR_MANAGER, Role.QA_LEAD, Role.QA_TESTER);
    private static final Set<Role> ARTIFACT_DELETE =
            EnumSet.of(Role.ADMIN, Role.SA_LEAD, Role.QA_LEAD);
    private static final Set<Role> CHANGE_REQUEST_DELETE =
            EnumSet.of(Role.ADMIN, Role.CR_MANAGER, Role.SA_LEAD, Role.QA_LEAD);
    private static final Set<Role> ADMIN_ONLY =
            EnumSet.of(Role.ADMIN);

    private Permissions() {
    }

    public static boolean canEditChangeRequests(Set<Role> roles) {
        return intersects(roles, CHANGE_REQUEST_EDIT);
    }

    public static boolean canEditBugs(Set<Role> roles) {
        return intersects(roles, BUG_EDIT);
    }

    public static boolean canEditTestCycles(Set<Role> roles) {
        return intersects(roles, TEST_CYCLE_EDIT);
    }

    public static boolean canEditTestCases(Set<Role> roles) {
        return intersects(roles, TEST_CASE_EDIT);
    }

    public static boolean canManageUsers(Set<Role> roles) {
        return intersects(roles, USER_MANAGE);
    }

    public static boolean canViewQaStats(Set<Role> roles) {
        return intersects(roles, QA_STATS_VIEW);
    }

    public static boolean canDeleteArtifacts(Set<Role> roles) {
        return intersects(roles, ARTIFACT_DELETE);
    }

    public static boolean canDeleteChangeRequests(Set<Role> roles) {
        return intersects(roles, CHANGE_REQUEST_DELETE);
    }

    public static boolean isAdmin(Set<Role> roles) {
        return intersects(roles, ADMIN_ONLY);
    }

    private static boolean intersects(Set<Role> roles, Set<Role> allowed) {
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        for (Role role : roles) {
            if (allowed.contains(role)) {
                return true;
            }
        }
        return false;
    }
}

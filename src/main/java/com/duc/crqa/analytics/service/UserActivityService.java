package com.duc.crqa.analytics.service;

import com.duc.crqa.analytics.dto.ActivityPing;
import com.duc.crqa.analytics.entity.UserActivity;
import com.duc.crqa.analytics.repository.UserActivityRepository;
import com.duc.crqa.security.CurrentUserService;
import com.duc.crqa.user.entity.User;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserActivityService {

    private final UserActivityRepository userActivityRepository;
    private final CurrentUserService currentUserService;

    public UserActivityService(UserActivityRepository userActivityRepository,
                               CurrentUserService currentUserService) {
        this.userActivityRepository = userActivityRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public void record(ActivityPing ping) {
        User user = currentUserService.currentUser();
        if (user == null || ping == null || ping.getPath() == null || ping.getSeconds() <= 0) {
            return;
        }
        UserActivity activity = new UserActivity();
        activity.setUser(user);
        activity.setFeature(featureOf(ping.getPath()));
        activity.setPath(ping.getPath().length() > 255 ? ping.getPath().substring(0, 255) : ping.getPath());
        activity.setDurationSeconds(ping.getSeconds());
        activity.setStartedAt(ping.getStartedAtEpochMs() == null
                ? LocalDateTime.now()
                : LocalDateTime.ofInstant(Instant.ofEpochMilli(ping.getStartedAtEpochMs()), ZoneId.systemDefault()));
        userActivityRepository.save(activity);
    }

    /** Maps a request path (which may include the context path) to a feature label. */
    public static String featureOf(String path) {
        if (path == null || path.isBlank()) {
            return "Other";
        }
        if (path.contains("/admin/analytics")) {
            return "Analytics";
        }
        if (path.contains("/admin/audit")) {
            return "Audit Log";
        }
        if (path.contains("/admin/export-all")) {
            return "Export All";
        }
        if (path.contains("/admin/users")) {
            return "Users";
        }
        if (path.contains("/admin/projects")) {
            return "Projects";
        }
        if (path.contains("/change-requests")) {
            return "Change Requests";
        }
        if (path.contains("/test-cycles")) {
            return "Test Cycles";
        }
        if (path.contains("/test-cases")) {
            return "Test Cases";
        }
        if (path.contains("/bugs")) {
            return "Bugs";
        }
        if (path.contains("/reports")) {
            return "Reports";
        }
        if (path.contains("/notifications")) {
            return "Notifications";
        }
        if (path.contains("/dashboard")) {
            return "Dashboard";
        }
        return "Other";
    }
}

package com.duc.crqa.user.service;

import com.duc.crqa.user.entity.User;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves a user reference typed into a bulk-import file — a username or a display name, matched without regard to
 * case — to that user's id. Built once from the current users so a whole file resolves without a query per row.
 */
public class UserLookup {

    private final Map<String, Long> byUsername = new HashMap<>();
    private final Map<String, Long> byDisplayName = new HashMap<>();

    public UserLookup(List<User> users) {
        for (User user : users) {
            if (user.getUsername() != null) {
                byUsername.putIfAbsent(user.getUsername().toLowerCase(Locale.ROOT), user.getId());
            }
            if (user.getDisplayName() != null) {
                byDisplayName.putIfAbsent(user.getDisplayName().toLowerCase(Locale.ROOT), user.getId());
            }
        }
    }

    /** The id of the user with this username or display name, or {@code null} when neither matches. */
    public Long resolve(String name) {
        if (name == null) {
            return null;
        }
        String key = name.trim().toLowerCase(Locale.ROOT);
        Long id = byUsername.get(key);
        return id != null ? id : byDisplayName.get(key);
    }
}

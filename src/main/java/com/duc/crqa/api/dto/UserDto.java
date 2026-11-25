package com.duc.crqa.api.dto;

import com.duc.crqa.common.enums.Role;
import com.duc.crqa.user.entity.User;
import java.util.Set;

public record UserDto(
        Long id,
        String username,
        String displayName,
        String email,
        boolean active,
        Set<Role> roles) {

    public static UserDto from(User user) {
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.isActive(),
                user.getRoles());
    }
}

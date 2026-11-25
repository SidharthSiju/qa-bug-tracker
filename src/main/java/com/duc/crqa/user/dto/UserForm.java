package com.duc.crqa.user.dto;

import com.duc.crqa.common.enums.Role;
import jakarta.validation.constraints.NotBlank;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserForm {

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Display name is required")
    private String displayName;

    private String email;

    private String password;

    private boolean active = true;

    private boolean emailNotifications = true;

    private Set<Role> roles = new HashSet<>();
}

package com.duc.crqa.user.dto;

import com.duc.crqa.common.enums.Role;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserSearchCriteria {

    private String search;

    private Role role;

    private Boolean active;
}

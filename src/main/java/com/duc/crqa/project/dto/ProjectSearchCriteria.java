package com.duc.crqa.project.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProjectSearchCriteria {

    private String search;

    private Boolean active;
}

package com.duc.crqa.cr.dto;

import com.duc.crqa.common.enums.Complexity;
import com.duc.crqa.common.enums.CrPriority;
import com.duc.crqa.common.enums.CrStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CrSearchCriteria {

    private String search;

    private Long projectId;

    private CrStatus status;

    private CrPriority priority;

    private Complexity complexity;

    private String sprintName;

    private Long qaLeadId;

    private Long saLeadId;

    private Long developmentOwnerId;

    private String fromDate;

    private String toDate;
}

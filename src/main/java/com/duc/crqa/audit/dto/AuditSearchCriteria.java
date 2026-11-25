package com.duc.crqa.audit.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AuditSearchCriteria {

    private String search;

    private String action;

    private String entityType;

    private String fromDate;

    private String toDate;
}

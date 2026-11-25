package com.duc.crqa.analytics.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AnalyticsSearchCriteria {

    private String fromDate;

    private String toDate;

    private Long userId;

    private String search;

    private String groupBy = "cr";
}

package com.duc.crqa.report.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class QaStatsSearchCriteria {

    private String search;

    private String fromDate;

    private String toDate;
}

package com.duc.crqa.testcycle.dto;

import com.duc.crqa.common.enums.TestCycleStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TestCycleSearchCriteria {

    private String search;

    private Long crId;

    private TestCycleStatus status;
}

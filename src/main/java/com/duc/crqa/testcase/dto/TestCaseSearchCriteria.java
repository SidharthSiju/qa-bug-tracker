package com.duc.crqa.testcase.dto;

import com.duc.crqa.common.enums.TestCaseStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TestCaseSearchCriteria {

    private String search;

    private Long crId;

    private Long cycleId;

    private TestCaseStatus status;
}

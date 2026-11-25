package com.duc.crqa.bug.dto;

import com.duc.crqa.common.enums.BugStatus;
import com.duc.crqa.common.enums.DefectCategory;
import com.duc.crqa.common.enums.Priority;
import com.duc.crqa.common.enums.Severity;
import com.duc.crqa.common.enums.SlaStatus;
import com.duc.crqa.common.enums.TestPhase;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class BugSearchCriteria {

    private String search;

    private Long crId;

    private BugStatus status;

    private Severity severity;

    private Priority priority;

    private Long assignedToId;

    private Long reportedById;

    private String application;

    private String module;

    private String environment;

    private String sprint;

    private DefectCategory defectCategory;

    private TestPhase testPhaseFound;

    private String fromDate;

    private String toDate;

    /** Filter on the derived SLA state (breached / approaching / within). */
    private SlaStatus sla;
}

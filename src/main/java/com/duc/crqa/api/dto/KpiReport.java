package com.duc.crqa.api.dto;

import com.duc.crqa.common.enums.BugStatus;
import com.duc.crqa.common.enums.CrStatus;
import com.duc.crqa.common.enums.Severity;
import java.util.Map;

public record KpiReport(
        Map<String, Long> kpis,
        Map<BugStatus, Long> bugStatusCounts,
        Map<Severity, Long> bugSeverityCounts,
        Map<CrStatus, Long> crStatusCounts) {
}

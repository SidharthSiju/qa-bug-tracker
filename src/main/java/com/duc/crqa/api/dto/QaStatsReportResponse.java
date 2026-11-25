package com.duc.crqa.api.dto;

import com.duc.crqa.report.service.QaStatisticsService.QaStatRow;
import com.duc.crqa.report.service.QaStatisticsService.QaStatSummary;
import org.springframework.data.domain.Page;

public record QaStatsReportResponse(QaStatSummary summary, Page<QaStatRow> rows) {
}

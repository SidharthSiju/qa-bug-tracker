package com.duc.crqa.api.dto;

import com.duc.crqa.report.service.TestQualityService.CrQualityRow;
import com.duc.crqa.report.service.TestQualityService.QualitySummary;
import org.springframework.data.domain.Page;

public record QualityReportResponse(QualitySummary summary, Page<CrQualityRow> rows) {
}

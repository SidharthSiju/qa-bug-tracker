package com.duc.crqa.api.controller;

import com.duc.crqa.api.dto.KpiReport;
import com.duc.crqa.api.dto.QaStatsReportResponse;
import com.duc.crqa.api.dto.QualityReportResponse;
import com.duc.crqa.cr.dto.CrSearchCriteria;
import com.duc.crqa.dashboard.service.DashboardService;
import com.duc.crqa.report.dto.QaStatsSearchCriteria;
import com.duc.crqa.report.service.QaStatisticsService;
import com.duc.crqa.report.service.TestQualityService;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportRestController {

    private final TestQualityService testQualityService;
    private final QaStatisticsService qaStatisticsService;
    private final DashboardService dashboardService;

    public ReportRestController(TestQualityService testQualityService,
                                QaStatisticsService qaStatisticsService,
                                DashboardService dashboardService) {
        this.testQualityService = testQualityService;
        this.qaStatisticsService = qaStatisticsService;
        this.dashboardService = dashboardService;
    }

    @GetMapping("/quality")
    public QualityReportResponse quality(@ModelAttribute CrSearchCriteria criteria,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        var summary = testQualityService.summary(criteria);
        var rows = testQualityService.page(criteria, PageRequest.of(page, size));
        return new QualityReportResponse(summary, rows);
    }

    @GetMapping("/qa-stats")
    @PreAuthorize("hasAnyRole('ADMIN','CR_MANAGER','QA_LEAD','QA_TESTER')")
    public QaStatsReportResponse qaStats(@ModelAttribute QaStatsSearchCriteria criteria,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        var summary = qaStatisticsService.summary(criteria);
        var rows = qaStatisticsService.page(criteria, PageRequest.of(page, size));
        return new QaStatsReportResponse(summary, rows);
    }

    @GetMapping("/kpis")
    public KpiReport kpis() {
        return new KpiReport(
                dashboardService.kpis(),
                dashboardService.bugStatusCounts(),
                dashboardService.bugSeverityCounts(),
                dashboardService.crStatusCounts());
    }
}

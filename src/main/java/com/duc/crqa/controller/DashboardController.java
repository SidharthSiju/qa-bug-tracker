package com.duc.crqa.controller;

import com.duc.crqa.common.util.SortSupport;
import com.duc.crqa.dashboard.service.DashboardService;
import com.duc.crqa.notification.repository.NotificationRepository;
import com.duc.crqa.report.dto.SprintSearchCriteria;
import com.duc.crqa.report.service.SprintReportService;
import com.duc.crqa.security.CurrentUserService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DashboardController {

    private static final Set<String> PROGRESS_SORTABLE = Set.of("crNumber", "project", "qaLead", "totalBugs",
            "openBugs", "criticalBugs", "retestingBugs", "closedBugs", "status", "plannedEndDate", "overdue");

    private final DashboardService dashboardService;
    private final SprintReportService sprintReportService;
    private final CurrentUserService currentUserService;
    private final NotificationRepository notificationRepository;

    public DashboardController(DashboardService dashboardService,
                               SprintReportService sprintReportService,
                               CurrentUserService currentUserService,
                               NotificationRepository notificationRepository) {
        this.dashboardService = dashboardService;
        this.sprintReportService = sprintReportService;
        this.currentUserService = currentUserService;
        this.notificationRepository = notificationRepository;
    }

    @GetMapping({"/", "/dashboard"})
    public String dashboard(@RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "10") int size,
                            @RequestParam(defaultValue = "") String sort,
                            @RequestParam(defaultValue = "asc") String dir,
                            Model model) {
        model.addAttribute("kpis", dashboardService.kpis());
        model.addAttribute("bugStatusCounts", dashboardService.bugStatusCounts());
        model.addAttribute("bugSeverityCounts", dashboardService.bugSeverityCounts());
        model.addAttribute("crStatusCounts", dashboardService.crStatusCounts());
        model.addAttribute("crProgressPage", dashboardService.crProgress(PageRequest.of(page, size,
                SortSupport.resolve(sort, dir, PROGRESS_SORTABLE, Sort.unsorted()))));
        model.addAttribute("sprintRows", sprintReportService.rows(new SprintSearchCriteria()));

        addChartData(model, "bugStatus", dashboardService.bugStatusCounts());
        addChartData(model, "bugSeverity", dashboardService.bugSeverityCounts());
        addChartData(model, "crStatus", dashboardService.crStatusCounts());
        Long userId = currentUserService.currentUserId();
        if (userId != null) {
            model.addAttribute("recentNotifications",
                    notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId)
                            .stream().limit(5).toList());
        }
        return "dashboard/dashboard";
    }

    private void addChartData(Model model, String prefix, Map<? extends Enum<?>, Long> counts) {
        model.addAttribute(prefix + "Labels", counts.keySet().stream().map(Enum::name).toList());
        model.addAttribute(prefix + "Values", List.copyOf(counts.values()));
    }
}

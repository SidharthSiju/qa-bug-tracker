package com.duc.crqa.analytics.controller;

import com.duc.crqa.analytics.dto.AnalyticsSearchCriteria;
import com.duc.crqa.analytics.service.AnalyticsService;
import com.duc.crqa.analytics.util.ExcelExporter;
import com.duc.crqa.common.util.SortSupport;
import com.duc.crqa.user.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/analytics")
@PreAuthorize("hasRole('ADMIN')")
public class AnalyticsController {

    private static final Set<String> USER_SORTABLE = Set.of("member", "username", "roles", "logins", "logouts",
            "crCreated", "crUpdated", "tcExecuted", "bugsRaised", "timeSpentSeconds");
    private static final Set<String> BUG_SORTABLE = Set.of("group", "total", "open", "critical", "avgFixHours");
    private static final Set<String> EXECUTION_SORTABLE = Set.of("member", "executed", "passed", "failed", "blocked",
            "notApplicable", "passRate");
    private static final Set<String> DEFECT_SORTABLE = Set.of("label", "count");
    private static final Set<String> TIME_SORTABLE = Set.of("member", "feature", "totalSeconds", "sessions");
    private static final Set<String> CR_CYCLE_SORTABLE = Set.of("crNumber", "crName", "project", "status", "created",
            "completed", "cycleDays");

    private final AnalyticsService analyticsService;
    private final UserService userService;

    public AnalyticsController(AnalyticsService analyticsService, UserService userService) {
        this.analyticsService = analyticsService;
        this.userService = userService;
    }

    @GetMapping
    public String index(@ModelAttribute AnalyticsSearchCriteria criteria,
                        @RequestParam(defaultValue = "users") String tab,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size,
                        @RequestParam(defaultValue = "") String sort,
                        @RequestParam(defaultValue = "asc") String dir,
                        Model model) {
        model.addAttribute("tab", tab);
        model.addAttribute("criteria", criteria);
        model.addAttribute("users", userService.listActive());
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);
        model.addAttribute("page", pageFor(tab, criteria, page, size, sort, dir));
        if ("defects".equals(tab)) {
            model.addAttribute("severityRows", analyticsService.defectsBySeverity(criteria));
            model.addAttribute("statusRows", analyticsService.defectsByStatus(criteria));
        }
        return "analytics/index";
    }

    @GetMapping("/export")
    public void export(@ModelAttribute AnalyticsSearchCriteria criteria,
                       @RequestParam(defaultValue = "users") String tab,
                       HttpServletResponse response) throws IOException {
        List<String> headers = new ArrayList<>();
        List<List<Object>> rows = new ArrayList<>();
        buildExport(tab, criteria, headers, rows);
        ExcelExporter.write(response, "Analytics", "analytics-" + tab + ".xlsx", headers, rows);
    }

    private Page<?> pageFor(String tab, AnalyticsSearchCriteria criteria, int page, int size, String sort, String dir) {
        return switch (tab) {
            case "bugs" -> AnalyticsService.page(analyticsService.bugGroups(criteria),
                    PageRequest.of(page, size, SortSupport.resolve(sort, dir, BUG_SORTABLE,
                            Sort.by(Sort.Direction.DESC, "total"))));
            case "execution" -> AnalyticsService.page(analyticsService.execution(criteria),
                    PageRequest.of(page, size, SortSupport.resolve(sort, dir, EXECUTION_SORTABLE,
                            Sort.by(Sort.Direction.DESC, "executed"))));
            case "defects" -> AnalyticsService.page(analyticsService.defectsBySeverity(criteria),
                    PageRequest.of(page, size, SortSupport.resolve(sort, dir, DEFECT_SORTABLE,
                            Sort.by(Sort.Direction.DESC, "count"))));
            case "time" -> AnalyticsService.page(analyticsService.timeSpent(criteria),
                    PageRequest.of(page, size, SortSupport.resolve(sort, dir, TIME_SORTABLE,
                            Sort.by(Sort.Direction.DESC, "totalSeconds"))));
            case "crcycle" -> AnalyticsService.page(analyticsService.crCycle(criteria),
                    PageRequest.of(page, size, SortSupport.resolve(sort, dir, CR_CYCLE_SORTABLE,
                            Sort.by(Sort.Direction.ASC, "crNumber"))));
            default -> AnalyticsService.page(analyticsService.userActivity(criteria),
                    PageRequest.of(page, size, SortSupport.resolve(sort, dir, USER_SORTABLE,
                            Sort.by(Sort.Direction.ASC, "member"))));
        };
    }

    private void buildExport(String tab, AnalyticsSearchCriteria criteria, List<String> headers,
                             List<List<Object>> rows) {
        switch (tab) {
            case "bugs" -> {
                headers.addAll(List.of("Group", "Total", "Open", "Critical", "Avg Fix (h)"));
                for (var row : analyticsService.bugGroups(criteria)) {
                    rows.add(List.of(row.group(), row.total(), row.open(), row.critical(), row.avgFixHours()));
                }
            }
            case "execution" -> {
                headers.addAll(List.of("Member", "Username", "Executed", "Passed", "Failed", "Blocked",
                        "Not Applicable", "Pass Rate %"));
                for (var row : analyticsService.execution(criteria)) {
                    rows.add(List.of(row.member(), row.username(), row.executed(), row.passed(), row.failed(),
                            row.blocked(), row.notApplicable(), row.passRate()));
                }
            }
            case "defects" -> {
                headers.addAll(List.of("Dimension", "Label", "Count"));
                analyticsService.defectsBySeverity(criteria)
                        .forEach(row -> rows.add(List.of("Severity", row.label(), row.count())));
                analyticsService.defectsByStatus(criteria)
                        .forEach(row -> rows.add(List.of("Status", row.label(), row.count())));
            }
            case "time" -> {
                headers.addAll(List.of("User", "Feature", "Time (min)", "Sessions"));
                for (var row : analyticsService.timeSpent(criteria)) {
                    rows.add(List.of(row.member(), row.feature(), Math.round(row.totalSeconds() / 60.0),
                            row.sessions()));
                }
            }
            case "crcycle" -> {
                headers.addAll(List.of("Epic Number", "Name", "Project", "Status", "Created", "Completed",
                        "Cycle Days"));
                for (var row : analyticsService.crCycle(criteria)) {
                    rows.add(List.of(row.crNumber(), row.crName(), row.project(), row.status(), row.created(),
                            row.completed(), row.cycleDays() == null ? "" : row.cycleDays()));
                }
            }
            default -> {
                headers.addAll(List.of("User", "Username", "Roles", "Logins", "Logouts", "CRs Created",
                        "CRs Updated", "Test Cases Executed", "Bugs Raised", "Time (min)"));
                for (var row : analyticsService.userActivity(criteria)) {
                    rows.add(List.of(row.member(), row.username(), row.roles(), row.logins(), row.logouts(),
                            row.crCreated(), row.crUpdated(), row.tcExecuted(), row.bugsRaised(),
                            Math.round(row.timeSpentSeconds() / 60.0)));
                }
            }
        }
    }
}

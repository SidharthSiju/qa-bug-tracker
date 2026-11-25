package com.duc.crqa.report.controller;

import com.duc.crqa.bug.dto.BugSearchCriteria;
import com.duc.crqa.bug.entity.Bug;
import com.duc.crqa.bug.service.BugService;
import com.duc.crqa.bug.service.SlaEscalationService;
import com.duc.crqa.cr.dto.CrSearchCriteria;
import com.duc.crqa.cr.entity.ChangeRequest;
import com.duc.crqa.cr.service.ChangeRequestService;
import com.duc.crqa.common.util.SortSupport;
import com.duc.crqa.dashboard.service.DashboardService;
import com.duc.crqa.project.service.ProjectService;
import com.duc.crqa.report.dto.QaStatsSearchCriteria;
import com.duc.crqa.report.dto.SprintSearchCriteria;
import com.duc.crqa.report.service.QaStatisticsService;
import com.duc.crqa.report.service.SlaReportService;
import com.duc.crqa.report.service.SprintReportService;
import com.duc.crqa.report.service.TestQualityService;
import com.duc.crqa.user.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/reports")
public class ReportController {

    private static final Set<String> BUG_SORTABLE = Set.of("bugNumber", "title", "defectCategory",
            "testPhaseFound", "sprint", "severity", "priority", "status", "cr.crNumber",
            "reportedBy.username", "assignedTo.username", "reportedAt");
    private static final Set<String> CR_SORTABLE = Set.of("crNumber", "crName", "project.projectCode",
            "priority", "complexity", "sprintName", "qaLead.username", "developmentOwner.username",
            "status", "plannedEndDate");
    private static final Set<String> QUALITY_SORTABLE = Set.of("crNumber", "project", "status",
            "totalTestCases", "executedTestCases", "passedTestCases", "failedTestCases", "blockedTestCases",
            "passRate", "totalBugs", "openBugs", "criticalBugs", "escapedDefects", "defectDensity",
            "qaCycleDays", "plannedEndDate", "overdue");
    private static final Set<String> SPRINT_SORTABLE = Set.of("sprint", "changeRequests", "totalTestCases",
            "executedTestCases", "passedTestCases", "failedTestCases", "blockedTestCases", "passRate",
            "totalBugs", "openBugs", "criticalBugs", "escapedDefects");
    private static final Set<String> SPRINT_CR_SORTABLE = Set.of("crNumber", "crName", "project", "status",
            "priority", "complexity", "totalTestCases", "executedTestCases", "passedTestCases",
            "failedTestCases", "blockedTestCases", "passRate", "totalBugs", "openBugs", "criticalBugs",
            "escapedDefects", "plannedEndDate");
    private static final Set<String> QA_STATS_SORTABLE = Set.of("displayName", "role", "executedTestCases",
            "passedTestCases", "failedTestCases", "blockedTestCases", "notApplicableTestCases", "passRate",
            "bugsRaised", "openBugs", "criticalBugs");

    private static final String QA_STATS_VIEW = "hasAnyRole('ADMIN','CR_MANAGER','QA_LEAD','QA_TESTER')";

    private final BugService bugService;
    private final ChangeRequestService changeRequestService;
    private final TestQualityService testQualityService;
    private final SprintReportService sprintReportService;
    private final QaStatisticsService qaStatisticsService;
    private final DashboardService dashboardService;
    private final UserService userService;
    private final ProjectService projectService;
    private final SlaReportService slaReportService;
    private final SlaEscalationService slaEscalationService;

    public ReportController(BugService bugService,
                            ChangeRequestService changeRequestService,
                            TestQualityService testQualityService,
                            SprintReportService sprintReportService,
                            QaStatisticsService qaStatisticsService,
                            DashboardService dashboardService,
                            UserService userService,
                            ProjectService projectService,
                            SlaReportService slaReportService,
                            SlaEscalationService slaEscalationService) {
        this.bugService = bugService;
        this.changeRequestService = changeRequestService;
        this.testQualityService = testQualityService;
        this.sprintReportService = sprintReportService;
        this.qaStatisticsService = qaStatisticsService;
        this.dashboardService = dashboardService;
        this.userService = userService;
        this.projectService = projectService;
        this.slaReportService = slaReportService;
        this.slaEscalationService = slaEscalationService;
    }

    @GetMapping
    public String index() {
        return "report/index";
    }

    @GetMapping("/quality")
    public String qualityReport(@ModelAttribute CrSearchCriteria criteria,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "10") int size,
                                @RequestParam(defaultValue = "") String sort,
                                @RequestParam(defaultValue = "asc") String dir,
                                Model model) {
        var summary = testQualityService.summary(criteria);
        model.addAttribute("summary", summary);
        model.addAttribute("page", testQualityService.page(criteria, PageRequest.of(page, size,
                SortSupport.resolve(sort, dir, QUALITY_SORTABLE, Sort.unsorted()))));
        model.addAttribute("criteria", criteria);
        addCrFormOptions(model);

        model.addAttribute("testStatusLabels",
                summary.testStatusCounts().keySet().stream().map(status -> status.name()).toList());
        model.addAttribute("testStatusValues",
                List.copyOf(summary.testStatusCounts().values()));

        var severityCounts = dashboardService.bugSeverityCounts();
        model.addAttribute("bugSeverityLabels",
                severityCounts.keySet().stream().map(severity -> severity.name()).toList());
        model.addAttribute("bugSeverityValues", List.copyOf(severityCounts.values()));

        return "report/quality";
    }

    @GetMapping("/quality.csv")
    public void exportQuality(@ModelAttribute CrSearchCriteria criteria, HttpServletResponse response)
            throws IOException {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"test_quality.csv\"");
        var writer = response.getWriter();
        writer.println("CR Number,Name,Project,Status,Total Test Cases,Executed,Passed,Failed,Blocked,"
                + "Not Applicable,Pass Rate %,Total Bugs,Open Bugs,Critical Bugs,Escaped Defects,"
                + "Defect Density,QA Cycle Days,Planned End,Overdue");
        for (var row : testQualityService.rows(criteria)) {
            writer.println(csv(row.crNumber()) + ","
                    + csv(row.crName()) + ","
                    + csv(row.project()) + ","
                    + csv(row.status().name()) + ","
                    + row.totalTestCases() + ","
                    + row.executedTestCases() + ","
                    + row.passedTestCases() + ","
                    + row.failedTestCases() + ","
                    + row.blockedTestCases() + ","
                    + row.notApplicableTestCases() + ","
                    + row.passRate() + ","
                    + row.totalBugs() + ","
                    + row.openBugs() + ","
                    + row.criticalBugs() + ","
                    + row.escapedDefects() + ","
                    + row.defectDensity() + ","
                    + row.qaCycleDays() + ","
                    + csv(row.plannedEndDate()) + ","
                    + (row.overdue() ? "Yes" : "No"));
        }
        writer.flush();
    }

    @GetMapping("/qa-stats")
    @PreAuthorize(QA_STATS_VIEW)
    public String qaStatsReport(@ModelAttribute QaStatsSearchCriteria criteria,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "10") int size,
                                @RequestParam(defaultValue = "displayName") String sort,
                                @RequestParam(defaultValue = "asc") String dir,
                                Model model) {
        var summary = qaStatisticsService.summary(criteria);
        var rows = qaStatisticsService.rows(criteria);
        model.addAttribute("summary", summary);
        model.addAttribute("page", qaStatisticsService.page(criteria, PageRequest.of(page, size,
                SortSupport.resolve(sort, dir, QA_STATS_SORTABLE, Sort.by(Sort.Direction.ASC, "displayName")))));
        model.addAttribute("criteria", criteria);

        model.addAttribute("qaMemberLabels", rows.stream().map(r -> r.displayName()).toList());
        model.addAttribute("qaMemberBugs", rows.stream().map(r -> r.bugsRaised()).toList());
        model.addAttribute("testOutcomeLabels", List.of("Passed", "Failed", "Blocked", "Not Applicable"));
        model.addAttribute("testOutcomeValues", List.of(
                summary.passedTestCases(), summary.failedTestCases(),
                summary.blockedTestCases(), summary.notApplicableTestCases()));
        return "report/qa-stats";
    }

    @GetMapping("/qa-stats.csv")
    @PreAuthorize(QA_STATS_VIEW)
    public void exportQaStats(@ModelAttribute QaStatsSearchCriteria criteria, HttpServletResponse response)
            throws IOException {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"qa_test_bug_statistics.csv\"");
        var writer = response.getWriter();
        writer.println("Member,Username,Role,Test Cases Executed,Passed,Failed,Blocked,Not Applicable,"
                + "Pass Rate %,Bugs Raised,Open Bugs,Critical Bugs");
        for (var row : qaStatisticsService.rows(criteria)) {
            writer.println(csv(row.displayName()) + ","
                    + csv(row.username()) + ","
                    + csv(row.role()) + ","
                    + row.executedTestCases() + ","
                    + row.passedTestCases() + ","
                    + row.failedTestCases() + ","
                    + row.blockedTestCases() + ","
                    + row.notApplicableTestCases() + ","
                    + row.passRate() + ","
                    + row.bugsRaised() + ","
                    + row.openBugs() + ","
                    + row.criticalBugs());
        }
        writer.flush();
    }

    @GetMapping("/bugs")
    public String bugReport(@ModelAttribute BugSearchCriteria criteria,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "10") int size,
                            @RequestParam(defaultValue = "reportedAt") String sort,
                            @RequestParam(defaultValue = "desc") String dir,
                            Model model) {
        Page<Bug> result = bugService.search(criteria, PageRequest.of(page, size,
                SortSupport.resolve(sort, dir, BUG_SORTABLE, Sort.by(Sort.Direction.DESC, "reportedAt"))));
        model.addAttribute("page", result);
        model.addAttribute("criteria", criteria);
        addBugFormOptions(model);
        return "report/bugs";
    }

    @GetMapping("/sprints")
    public String sprintReport(@ModelAttribute SprintSearchCriteria criteria,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "10") int size,
                               @RequestParam(defaultValue = "") String sort,
                               @RequestParam(defaultValue = "asc") String dir,
                               Model model) {
        model.addAttribute("page", sprintReportService.page(criteria, PageRequest.of(page, size,
                SortSupport.resolve(sort, dir, SPRINT_SORTABLE, Sort.unsorted()))));
        model.addAttribute("criteria", criteria);
        model.addAttribute("summary", sprintReportService.summary(criteria));
        return "report/sprints";
    }

    @GetMapping("/sprints.csv")
    public void exportSprints(@ModelAttribute SprintSearchCriteria criteria, HttpServletResponse response)
            throws IOException {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"sprint_report.csv\"");
        var writer = response.getWriter();
        writer.println("Sprint,Change Requests,Total Test Cases,Executed,Passed,Failed,Blocked,"
                + "Pass Rate %,Total Bugs,Open Bugs,Critical Bugs,Escaped Defects");
        for (var row : sprintReportService.rows(criteria)) {
            writer.println(csv(row.sprint()) + ","
                    + row.changeRequests() + ","
                    + row.totalTestCases() + ","
                    + row.executedTestCases() + ","
                    + row.passedTestCases() + ","
                    + row.failedTestCases() + ","
                    + row.blockedTestCases() + ","
                    + row.passRate() + ","
                    + row.totalBugs() + ","
                    + row.openBugs() + ","
                    + row.criticalBugs() + ","
                    + row.escapedDefects());
        }
        writer.flush();
    }

    @GetMapping("/sprint-crs")
    public String sprintChangeRequests(@RequestParam String sprint,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "10") int size,
                                       @RequestParam(defaultValue = "crNumber") String sort,
                                       @RequestParam(defaultValue = "asc") String dir,
                                       Model model) {
        model.addAttribute("sprint", sprint);
        model.addAttribute("page", sprintReportService.changeRequestsForSprint(sprint, PageRequest.of(page, size,
                SortSupport.resolve(sort, dir, SPRINT_CR_SORTABLE, Sort.by(Sort.Direction.ASC, "crNumber")))));
        return "report/sprint-crs";
    }

    @GetMapping("/cr")
    public String crReport(@ModelAttribute CrSearchCriteria criteria,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "10") int size,
                           @RequestParam(defaultValue = "createdAt") String sort,
                           @RequestParam(defaultValue = "desc") String dir,
                           Model model) {
        Page<ChangeRequest> result = changeRequestService.search(criteria, PageRequest.of(page, size,
                SortSupport.resolve(sort, dir, CR_SORTABLE, Sort.by(Sort.Direction.DESC, "createdAt"))));
        model.addAttribute("page", result);
        model.addAttribute("criteria", criteria);
        addCrFormOptions(model);
        return "report/crs";
    }

    @GetMapping("/sla")
    public String slaReport(Model model) {
        model.addAttribute("report", slaReportService.build());
        return "report/sla";
    }

    @GetMapping("/sla.csv")
    public void exportSla(HttpServletResponse response) throws IOException {
        SlaReportService.SlaReport report = slaReportService.build();
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"sla_aging.csv\"");
        var writer = response.getWriter();
        writer.println("Dimension,Group,Open,Breached,Approaching,Oldest (days)");
        writeSlaRows(writer, "Severity", report.bySeverity());
        writeSlaRows(writer, "Assignee", report.byAssignee());
        writeSlaRows(writer, "Change Request", report.byChangeRequest());
        writer.flush();
    }

    private void writeSlaRows(java.io.PrintWriter writer, String dimension,
                              List<SlaReportService.SlaGroupRow> rows) {
        for (SlaReportService.SlaGroupRow row : rows) {
            writer.println(csv(dimension) + "," + csv(row.group()) + "," + row.total() + ","
                    + row.breached() + "," + row.approaching() + "," + row.oldestAgeDays());
        }
    }

    /** ADMIN action: run the SLA escalation sweep now instead of waiting for the scheduled run. */
    @PostMapping("/sla/escalate")
    @PreAuthorize("hasRole('ADMIN')")
    public String runSlaEscalation(RedirectAttributes redirectAttributes) {
        int escalated = slaEscalationService.escalateNow();
        redirectAttributes.addFlashAttribute("success", escalated == 0
                ? "No bug needed escalating (nothing is past its SLA, or everything was escalated recently)"
                : escalated + " bug(s) escalated to their owners");
        return "redirect:/reports/sla";
    }

    @GetMapping("/bugs.csv")
    public void exportBugs(@ModelAttribute BugSearchCriteria criteria, HttpServletResponse response) throws IOException {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"bugs.csv\"");
        var writer = response.getWriter();
        writer.println("Bug Number,CR,Title,Severity,Priority,Status,Application,Module,Sprint,"
                + "Defect Category,Test Phase,External Defect Id,Environment,"
                + "Reported By,Assigned To,Reported At,Resolution");
        for (Bug bug : bugService.search(criteria, Pageable.unpaged()).getContent()) {
            writer.println(csv(bug.getBugNumber()) + ","
                    + csv(bug.getCr() == null ? "" : bug.getCr().getCrNumber()) + ","
                    + csv(bug.getTitle()) + ","
                    + csv(bug.getSeverity().name()) + ","
                    + csv(bug.getPriority().name()) + ","
                    + csv(bug.getStatus().name()) + ","
                    + csv(bug.getApplication()) + ","
                    + csv(bug.getModule()) + ","
                    + csv(bug.getSprint()) + ","
                    + csv(bug.getDefectCategory() == null ? "" : bug.getDefectCategory().getLabel()) + ","
                    + csv(bug.getTestPhaseFound() == null ? "" : bug.getTestPhaseFound().name()) + ","
                    + csv(bug.getExternalDefectId()) + ","
                    + csv(bug.getEnvironment()) + ","
                    + csv(bug.getReportedBy() == null ? "" : bug.getReportedBy().displayLabel()) + ","
                    + csv(bug.getAssignedTo() == null ? "" : bug.getAssignedTo().displayLabel()) + ","
                    + csv(bug.getReportedAt() == null ? "" : bug.getReportedAt().toString()) + ","
                    + csv(bug.getResolution() == null ? "" : bug.getResolution().name()));
        }
        writer.flush();
    }

    @GetMapping("/crs.csv")
    public void exportCrs(@ModelAttribute CrSearchCriteria criteria, HttpServletResponse response) throws IOException {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"change_requests.csv\"");
        var writer = response.getWriter();
        writer.println("CR Number,Name,Project,Priority,Complexity,Sprint Name,QA Lead,Development Owner,Status,"
                + "Planned Start,Planned End");
        for (ChangeRequest cr : changeRequestService.search(criteria, Pageable.unpaged()).getContent()) {
            writer.println(csv(cr.getCrNumber()) + ","
                    + csv(cr.getCrName()) + ","
                    + csv(cr.getProject() == null ? "" : cr.getProject().getProjectCode()) + ","
                    + csv(cr.getPriority() == null ? "" : cr.getPriority().name()) + ","
                    + csv(cr.getComplexity() == null ? "" : cr.getComplexity().name()) + ","
                    + csv(cr.getSprintName()) + ","
                    + csv(cr.getSaLead() == null ? "" : cr.getSaLead().displayLabel()) + ","
                    + csv(cr.getQaLead() == null ? "" : cr.getQaLead().displayLabel()) + ","
                    + csv(cr.getDevelopmentOwner() == null ? "" : cr.getDevelopmentOwner().displayLabel()) + ","
                    + csv(cr.getStatus().name()) + ","
                    + csv(cr.getPlannedStartDate() == null ? "" : cr.getPlannedStartDate().toString()) + ","
                    + csv(cr.getPlannedEndDate() == null ? "" : cr.getPlannedEndDate().toString()));
        }
        writer.flush();
    }

    private void addBugFormOptions(Model model) {
        model.addAttribute("crs", changeRequestService.findAll());
        model.addAttribute("developers", userService.findDevelopers());
        model.addAttribute("qaUsers", userService.findQaUsers());
    }

    private void addCrFormOptions(Model model) {
        model.addAttribute("projects", projectService.listActive());
        model.addAttribute("developers", userService.findDevelopers());
        model.addAttribute("qaLeads", userService.findQaUsers());
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}

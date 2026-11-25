package com.duc.crqa.config;

import com.duc.crqa.bug.entity.Bug;
import com.duc.crqa.bug.entity.BugComment;
import com.duc.crqa.bug.entity.BugHistory;
import com.duc.crqa.bug.repository.BugCommentRepository;
import com.duc.crqa.bug.repository.BugHistoryRepository;
import com.duc.crqa.bug.repository.BugRepository;
import com.duc.crqa.common.enums.BugStatus;
import com.duc.crqa.common.enums.CommentType;
import com.duc.crqa.common.enums.Complexity;
import com.duc.crqa.common.enums.CrPriority;
import com.duc.crqa.common.enums.CrStatus;
import com.duc.crqa.common.enums.DefectCategory;
import com.duc.crqa.common.enums.Priority;
import com.duc.crqa.common.enums.Resolution;
import com.duc.crqa.common.enums.Role;
import com.duc.crqa.common.enums.Severity;
import com.duc.crqa.common.enums.TestCaseStatus;
import com.duc.crqa.common.enums.TestCycleStatus;
import com.duc.crqa.common.enums.TestPhase;
import com.duc.crqa.cr.entity.ChangeRequest;
import com.duc.crqa.cr.repository.ChangeRequestRepository;
import com.duc.crqa.project.entity.Project;
import com.duc.crqa.project.repository.ProjectRepository;
import com.duc.crqa.testcase.entity.TestCase;
import com.duc.crqa.testcase.repository.TestCaseRepository;
import com.duc.crqa.testcycle.entity.TestCycle;
import com.duc.crqa.testcycle.repository.TestCycleRepository;
import com.duc.crqa.user.entity.User;
import com.duc.crqa.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
public class DevDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ChangeRequestRepository changeRequestRepository;
    private final TestCycleRepository testCycleRepository;
    private final TestCaseRepository testCaseRepository;
    private final BugRepository bugRepository;
    private final BugCommentRepository bugCommentRepository;
    private final BugHistoryRepository bugHistoryRepository;
    private final PasswordEncoder passwordEncoder;

    public DevDataInitializer(UserRepository userRepository,
                              ProjectRepository projectRepository,
                              ChangeRequestRepository changeRequestRepository,
                              TestCycleRepository testCycleRepository,
                              TestCaseRepository testCaseRepository,
                              BugRepository bugRepository,
                              BugCommentRepository bugCommentRepository,
                              BugHistoryRepository bugHistoryRepository,
                              PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.changeRequestRepository = changeRequestRepository;
        this.testCycleRepository = testCycleRepository;
        this.testCaseRepository = testCaseRepository;
        this.bugRepository = bugRepository;
        this.bugCommentRepository = bugCommentRepository;
        this.bugHistoryRepository = bugHistoryRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }

        User admin = user("admin", "System Administrator", "admin@example.com", "admin123", Role.ADMIN);
        User crManager = user("cr.manager", "CR Manager", "cr.manager@example.com", "cr123", Role.CR_MANAGER);
        User qaLead = user("qa.lead", "QA Lead", "qa.lead@example.com", "qa123", Role.QA_LEAD);
        User qaTester = user("qa.tester", "QA Tester", "qa.tester@example.com", "qa123", Role.QA_TESTER);
        User developer = user("developer", "Developer", "developer@example.com", "dev123", Role.DEVELOPER);
        user("viewer", "Read Only Viewer", "viewer@example.com", "view123", Role.QA_VIEWER);
        User saLead = user("sa.lead", "SA Lead", "sa.lead@example.com", "qa123", Role.SA_LEAD);

        Project payment = project("PAYMENT", "Payment Portal", "Customer payment processing");
        Project crm = project("CRM", "CRM Platform", "Customer relationship management");
        Project billing = project("BILLING", "Billing System", "Invoice and billing");
        project("DEFAULT", "Default", "Default project for change requests without an assigned project");

        ChangeRequest cr1 = cr("CR-2026-000001", "Payment confirmation update", payment,
                "UAT", CrPriority.HIGH, Complexity.HIGH, developer, qaLead, saLead,
                CrStatus.QA_IN_PROGRESS, LocalDateTime.now().minusDays(10), LocalDateTime.now().plusDays(5),
                admin);
        ChangeRequest cr2 = cr("CR-2026-000002", "CRM contact import", crm,
                "SIT", CrPriority.MEDIUM, Complexity.MEDIUM, developer, qaLead, saLead,
                CrStatus.READY_FOR_QA, LocalDateTime.now().minusDays(5), LocalDateTime.now().plusDays(12),
                admin);
        ChangeRequest cr3 = cr("CR-2026-000003", "Billing tax calculation", billing,
                "DEV", CrPriority.HIGH, Complexity.HIGH, developer, qaLead, saLead,
                CrStatus.DEVELOPMENT, LocalDateTime.now().minusDays(2), LocalDateTime.now().plusDays(20),
                crManager);

        Project[] sampleProjects = {payment, crm, billing};
        String[] environments = {"DEV", "SIT", "UAT", "PROD"};
        CrPriority[] crPriorities = CrPriority.values();
        Complexity[] complexities = Complexity.values();
        CrStatus[] crStatuses = CrStatus.values();
        for (int i = 4; i <= 28; i++) {
            Project project = sampleProjects[i % sampleProjects.length];
            cr(String.format("CR-2026-%06d", i),
                    "Sample CR " + i + " - " + project.getProjectName(),
                    project,
                    environments[i % environments.length],
                    crPriorities[i % crPriorities.length],
                    complexities[i % complexities.length],
                    developer,
                    qaLead,
                    saLead,
                    crStatuses[i % crStatuses.length],
                    LocalDateTime.now().minusDays(i + 1),
                    LocalDateTime.now().plusDays((i * 3) % 30 + 1),
                    admin);
        }

        TestCycle cycle1 = cycle(cr1, 1, "Payment Cycle 1", TestCycleStatus.IN_PROGRESS, qaLead);
        TestCycle cycle2 = cycle(cr2, 1, "CRM Cycle 1", TestCycleStatus.NOT_STARTED, qaLead);

        TestCase tc1 = testCase("TC-2026-000001", cr1, cycle1, "Verify payment confirmation", TestCaseStatus.PASS, qaTester);
        TestCase tc2 = testCase("TC-2026-000002", cr1, cycle1, "Verify payment failure path", TestCaseStatus.FAIL, qaTester);
        TestCase tc3 = testCase("TC-2026-000003", cr2, cycle2, "Verify contact import", TestCaseStatus.NOT_EXECUTED, null);

        Bug bug1 = bug("BUG-2026-000001", cr1, tc2, "Payment confirmation fails intermittently",
                "Confirmation page does not render after successful payment",
                Severity.CRITICAL, Priority.P1, BugStatus.RETESTING, "UAT", "Payment Portal", "Confirmation",
                qaTester, developer, LocalDateTime.now().minusDays(4));
        bug1.setAssignedAt(LocalDateTime.now().minusDays(4));
        bug1.setFixedAt(LocalDateTime.now().minusDays(1));
        bug1 = bugRepository.save(bug1);
        addHistory(bug1, "OPEN", null, BugStatus.OPEN, qaTester);
        addHistory(bug1, "ASSIGN", BugStatus.OPEN, BugStatus.ASSIGNED, qaTester);
        addHistory(bug1, "MARK_FIXED", BugStatus.IN_DEVELOPMENT, BugStatus.FIXED, developer);
        addHistory(bug1, "START_RETEST", BugStatus.READY_FOR_RETEST, BugStatus.RETESTING, qaTester);
        addComment(bug1, qaTester, CommentType.COMMENT, "Retesting this now.");

        Bug bug2 = bug("BUG-2026-000002", cr1, null, "Payment total rounded incorrectly",
                "Total amount differs by one cent for currency conversion",
                Severity.MAJOR, Priority.P2, BugStatus.IN_DEVELOPMENT, "UAT", "Payment Portal", "Total",
                qaTester, developer, LocalDateTime.now().minusDays(3));
        bug2.setAssignedAt(LocalDateTime.now().minusDays(3));
        bug2 = bugRepository.save(bug2);
        addHistory(bug2, "ASSIGN", BugStatus.OPEN, BugStatus.ASSIGNED, qaTester);

        Bug bug3 = bug("BUG-2026-000003", cr2, null, "CRM import fails for large files",
                "Import stops after 10,000 records without an error",
                Severity.BLOCKER, Priority.P1, BugStatus.OPEN, "SIT", "CRM", "Import",
                qaLead, null, LocalDateTime.now().minusDays(1));
        bugRepository.save(bug3);
        addHistory(bug3, "OPEN", null, BugStatus.OPEN, qaLead);

        Bug bug4 = bug("BUG-2026-000004", cr1, null, "Confirmation email not sent",
                "No email is sent after payment confirmation",
                Severity.MINOR, Priority.P3, BugStatus.CLOSED, "UAT", "Payment Portal", "Notification",
                qaTester, developer, LocalDateTime.now().minusDays(8));
        bug4.setAssignedAt(LocalDateTime.now().minusDays(8));
        bug4.setFixedAt(LocalDateTime.now().minusDays(6));
        bug4.setRetestedAt(LocalDateTime.now().minusDays(5));
        bug4.setClosedAt(LocalDateTime.now().minusDays(5));
        bug4.setResolution(Resolution.FIXED);
        bugRepository.save(bug4);
        addHistory(bug4, "RETEST_PASS", BugStatus.RETESTING, BugStatus.CLOSED, qaTester);

        ChangeRequest[] bugCrs = {cr1, cr2, cr3};
        Severity[] severities = Severity.values();
        Priority[] priorities = Priority.values();
        BugStatus[] bugStatuses = BugStatus.values();
        String[] applications = {"Payment Portal", "CRM", "Billing System", "Mobile App", "Reporting"};
        String[] modules = {"Confirmation", "Import", "Total", "Notification", "Auth", "Export"};
        for (int i = 5; i <= 34; i++) {
            ChangeRequest bugCr = bugCrs[i % bugCrs.length];
            User reporter = (i % 2 == 0) ? qaTester : qaLead;
            User assignee = (i % 3 == 0) ? developer : null;
            Bug sampleBug = bug(String.format("BUG-2026-%06d", i),
                    bugCr,
                    null,
                    "Sample bug " + i + " - " + applications[i % applications.length] + " " + modules[i % modules.length],
                    "Generated sample defect for report search and pagination testing.",
                    severities[i % severities.length],
                    priorities[i % priorities.length],
                    bugStatuses[i % bugStatuses.length],
                    environments[i % environments.length],
                    applications[i % applications.length],
                    modules[i % modules.length],
                    reporter,
                    assignee,
                    LocalDateTime.now().minusDays(i));
            bugRepository.save(sampleBug);
        }
    }

    private User user(String username, String displayName, String email, String rawPassword, Role... roles) {
        User user = new User();
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setActive(true);
        user.setRoles(Set.of(roles));
        return userRepository.save(user);
    }

    private Project project(String code, String name, String description) {
        Project project = new Project();
        project.setProjectCode(code);
        project.setProjectName(name);
        project.setDescription(description);
        project.setActive(true);
        return projectRepository.save(project);
    }

    private ChangeRequest cr(String number, String name, Project project, String environment,
                             CrPriority priority, Complexity complexity, User developmentOwner, User qaLead,
                             User saLead, CrStatus status, LocalDateTime plannedStart, LocalDateTime plannedEnd,
                             User createdBy) {
        ChangeRequest cr = new ChangeRequest();
        cr.setCrNumber(number);
        cr.setCrName(name);
        cr.setDescription(name + " — sample change request for QA tracking.");
        cr.setProject(project);
        cr.setEnvironment(environment);
        cr.setPriority(priority);
        cr.setComplexity(complexity);
        cr.setSprintName("Sprint 24");
        cr.setStoryNumber("STORY-" + number);
        cr.setDevelopmentOwner(developmentOwner);
        cr.setQaLead(qaLead);
        cr.setSaLead(saLead);
        cr.setStatus(status);
        cr.setPlannedStartDate(plannedStart);
        cr.setPlannedEndDate(plannedEnd);
        cr.setDacSubmitDate(plannedStart == null ? null : plannedStart.minusDays(7));
        cr.setHldSubmitDate(plannedStart == null ? null : plannedStart.minusDays(3));
        cr.setCreatedBy(createdBy.getId());
        cr.setUpdatedBy(createdBy.getId());
        return changeRequestRepository.save(cr);
    }

    private TestCycle cycle(ChangeRequest cr, int number, String name, TestCycleStatus status, User createdBy) {
        TestCycle cycle = new TestCycle();
        cycle.setCr(cr);
        cycle.setCycleNumber(number);
        cycle.setName(name);
        cycle.setStatus(status);
        cycle.setStartDate(status == TestCycleStatus.NOT_STARTED ? null : LocalDateTime.now().minusDays(3));
        cycle.setEndDate(null);
        cycle.setCreatedBy(createdBy.getId());
        return testCycleRepository.save(cycle);
    }

    private TestCase testCase(String number, ChangeRequest cr, TestCycle cycle, String title,
                              TestCaseStatus status, User executedBy) {
        TestCase testCase = new TestCase();
        testCase.setTestCaseNumber(number);
        testCase.setCr(cr);
        testCase.setCycle(cycle);
        testCase.setTitle(title);
        testCase.setDescription(title);
        testCase.setPrecondition("Sample precondition for " + title);
        testCase.setTestSteps("1. Open the application\n2. Perform " + title);
        testCase.setPriority(Priority.P2);
        testCase.setLabels("regression");
        testCase.setIssueType("Test");
        testCase.setStoryLink(cr == null ? null : cr.getStoryNumber());
        testCase.setEpicLink(cr == null ? null : cr.getCrNumber());
        testCase.setExpectedResult("Expected behaviour for " + title);
        testCase.setActualResult(status == TestCaseStatus.PASS || status == TestCaseStatus.FAIL
                ? "Observed behaviour for " + title : null);
        testCase.setStatus(status);
        testCase.setExecutedBy(executedBy == null ? null : executedBy.getId());
        testCase.setExecutedAt(executedBy == null ? null : LocalDateTime.now().minusDays(1));
        return testCaseRepository.save(testCase);
    }

    private Bug bug(String number, ChangeRequest cr, TestCase testCase, String title, String description,
                    Severity severity, Priority priority, BugStatus status, String environment,
                    String application, String module, User reportedBy, User assignedTo, LocalDateTime reportedAt) {
        Bug bug = new Bug();
        bug.setBugNumber(number);
        bug.setCr(cr);
        bug.setTestCase(testCase);
        bug.setTitle(title);
        bug.setDescription(description);
        bug.setStepsToReproduce("1. Open application\n2. Reproduce the described issue");
        bug.setExpectedResult("The operation should complete without errors.");
        bug.setActualResult(description);
        bug.setSeverity(severity);
        bug.setPriority(priority);
        bug.setStatus(status);
        bug.setEnvironment(environment);
        bug.setApplication(application);
        bug.setModule(module);
        bug.setSprint("Sprint 24");
        bug.setDefectCategory(categoryFor(status));
        bug.setTestPhaseFound(TestPhase.fromValue(environment));
        bug.setReportedBy(reportedBy);
        bug.setAssignedTo(assignedTo);
        bug.setReportedAt(reportedAt);
        return bug;
    }

    private DefectCategory categoryFor(BugStatus status) {
        return switch (status) {
            case CLOSED -> DefectCategory.SIT_FIXED_VERIFIED;
            case REJECTED -> DefectCategory.NOT_A_BUG;
            case REOPENED -> DefectCategory.SIT_REGRESSION_REOPENED_IN_UAT;
            default -> DefectCategory.SIT_OPEN;
        };
    }

    private void addHistory(Bug bug, String action, BugStatus oldStatus, BugStatus newStatus, User performedBy) {
        BugHistory history = new BugHistory();
        history.setBug(bug);
        history.setAction(action);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setPerformedBy(performedBy.getId());
        bugHistoryRepository.save(history);
    }

    private void addComment(Bug bug, User author, CommentType type, String text) {
        BugComment comment = new BugComment();
        comment.setBug(bug);
        comment.setComment(text);
        comment.setCommentType(type);
        comment.setCreatedBy(author.getId());
        bugCommentRepository.save(comment);
    }
}

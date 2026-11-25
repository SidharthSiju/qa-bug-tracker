package com.duc.crqa.report.service;

import com.duc.crqa.bug.entity.Bug;
import com.duc.crqa.bug.repository.BugRepository;
import com.duc.crqa.common.util.CsvUtil;
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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExportAllService {

    public static final String USER_PLACEHOLDER_PASSWORD = "ChangeMe123!";

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ChangeRequestRepository changeRequestRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestCycleRepository testCycleRepository;
    private final BugRepository bugRepository;

    public ExportAllService(UserRepository userRepository,
                            ProjectRepository projectRepository,
                            ChangeRequestRepository changeRequestRepository,
                            TestCaseRepository testCaseRepository,
                            TestCycleRepository testCycleRepository,
                            BugRepository bugRepository) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.changeRequestRepository = changeRequestRepository;
        this.testCaseRepository = testCaseRepository;
        this.testCycleRepository = testCycleRepository;
        this.bugRepository = bugRepository;
    }

    @Transactional(readOnly = true)
    public byte[] exportAllZip() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("01_users.csv", usersCsv());
        files.put("02_projects.csv", projectsCsv());
        files.put("03_change_requests.csv", changeRequestsCsv());
        files.put("04_test_cases.csv", testCasesCsv());
        files.put("05_test_cycles.csv", testCyclesCsv());
        files.put("06_bugs.csv", bugsCsv());
        return zip(files);
    }

    private String usersCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append(row("username", "displayName", "email", "password", "active", "roles"));
        for (User user : userRepository.findAll(Sort.by(Sort.Direction.ASC, "username"))) {
            String roles = user.getRoles() == null ? "" : user.getRoles().stream()
                    .map(Enum::name).sorted().collect(Collectors.joining(","));
            sb.append(row(user.getUsername(), user.getDisplayName(), user.getEmail(),
                    USER_PLACEHOLDER_PASSWORD, user.isActive(), roles));
        }
        return sb.toString();
    }

    private String projectsCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append(row("projectCode", "projectName", "description", "active"));
        for (Project project : projectRepository.findAll(Sort.by(Sort.Direction.ASC, "projectCode"))) {
            sb.append(row(project.getProjectCode(), project.getProjectName(),
                    project.getDescription(), project.isActive()));
        }
        return sb.toString();
    }

    private String changeRequestsCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append(row("crNumber", "crName", "projectCode", "environment", "sprintName", "storyNumber", "priority",
                "complexity", "saLeadUsername", "developmentOwnerUsername", "qaLeadUsername",
                "plannedStartDate", "plannedEndDate", "actualStartDate", "actualEndDate", "dacSubmitDate",
                "hldSubmitDate", "description"));
        for (ChangeRequest cr : changeRequestRepository.findAllByOrderByCreatedAtDesc()) {
            sb.append(row(cr.getCrNumber(), cr.getCrName(),
                    cr.getProject() == null ? null : cr.getProject().getProjectCode(),
                    cr.getEnvironment(), cr.getSprintName(), cr.getStoryNumber(),
                    cr.getPriority() == null ? null : cr.getPriority().name(),
                    cr.getComplexity() == null ? null : cr.getComplexity().name(),
                    username(cr.getSaLead()), username(cr.getDevelopmentOwner()), username(cr.getQaLead()),
                    date(cr.getPlannedStartDate()), date(cr.getPlannedEndDate()),
                    date(cr.getActualStartDate()), date(cr.getActualEndDate()),
                    date(cr.getDacSubmitDate()), date(cr.getHldSubmitDate()),
                    cr.getDescription()));
        }
        return sb.toString();
    }

    private String testCasesCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append(row("crNumber", "cycleNumber", "title", "description", "precondition", "testSteps",
                "expectedResult", "priority", "labels", "issueType", "issueLink"));
        for (TestCase testCase : testCaseRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))) {
            sb.append(row(testCase.getCr() == null ? null : testCase.getCr().getCrNumber(),
                    testCase.getCycle() == null ? null : testCase.getCycle().getCycleNumber(),
                    testCase.getTitle(), testCase.getDescription(), testCase.getPrecondition(),
                    testCase.getTestSteps(), testCase.getExpectedResult(),
                    testCase.getPriority() == null ? null : testCase.getPriority().name(),
                    testCase.getLabels(), testCase.getIssueType(), testCase.getIssueLink()));
        }
        return sb.toString();
    }

    private String testCyclesCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append(row("crNumber", "cycleNumber", "name", "status", "startDate", "endDate"));
        for (TestCycle cycle : testCycleRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))) {
            sb.append(row(cycle.getCr() == null ? null : cycle.getCr().getCrNumber(),
                    cycle.getCycleNumber(), cycle.getName(),
                    cycle.getStatus() == null ? null : cycle.getStatus().name(),
                    date(cycle.getStartDate()), date(cycle.getEndDate())));
        }
        return sb.toString();
    }

    private String bugsCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append(row("Defect ID", "Sprint", "CR Number", "Summary", "Description", "Defect Category",
                "Test Phase Found", "Severity", "Priority", "Status", "Environment", "Application",
                "Module", "Logged By", "Assigned To", "Steps to Reproduce", "Expected Result", "Actual Result"));
        for (Bug bug : bugRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))) {
            sb.append(row(bug.getExternalDefectId(), bug.getSprint(),
                    bug.getCr() == null ? null : bug.getCr().getCrNumber(),
                    bug.getTitle(), bug.getDescription(),
                    bug.getDefectCategory() == null ? null : bug.getDefectCategory().getLabel(),
                    bug.getTestPhaseFound() == null ? null : bug.getTestPhaseFound().name(),
                    bug.getSeverity() == null ? null : bug.getSeverity().name(),
                    bug.getPriority() == null ? null : bug.getPriority().name(),
                    bug.getStatus() == null ? null : bug.getStatus().name(),
                    bug.getEnvironment(), bug.getApplication(), bug.getModule(),
                    username(bug.getReportedBy()), username(bug.getAssignedTo()),
                    bug.getStepsToReproduce(), bug.getExpectedResult(), bug.getActualResult()));
        }
        return sb.toString();
    }

    private String row(Object... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            Object value = values[i];
            sb.append(CsvUtil.escape(value == null ? null : String.valueOf(value)));
        }
        return sb.append('\n').toString();
    }

    private String username(User user) {
        return user == null ? null : user.getUsername();
    }

    private String date(LocalDateTime value) {
        return value == null ? null : value.toLocalDate().toString();
    }

    private byte[] zip(Map<String, String> files) {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(buffer)) {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.finish();
            return buffer.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to build export archive", ex);
        }
    }
}

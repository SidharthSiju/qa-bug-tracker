package com.duc.crqa.report.service;

import com.duc.crqa.bug.entity.Bug;
import com.duc.crqa.bug.repository.BugRepository;
import com.duc.crqa.common.enums.BugStatus;
import com.duc.crqa.common.enums.Complexity;
import com.duc.crqa.common.enums.CrPriority;
import com.duc.crqa.common.enums.CrStatus;
import com.duc.crqa.common.enums.Severity;
import com.duc.crqa.common.enums.TestCaseStatus;
import com.duc.crqa.cr.entity.ChangeRequest;
import com.duc.crqa.cr.repository.ChangeRequestRepository;
import com.duc.crqa.common.util.InMemorySort;
import com.duc.crqa.report.dto.SprintSearchCriteria;
import com.duc.crqa.testcase.repository.TestCaseRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SprintReportService {

    private static final List<BugStatus> CLOSED_STATUSES = List.of(BugStatus.CLOSED, BugStatus.REJECTED);
    private static final List<Severity> CRITICAL_SEVERITIES = List.of(Severity.BLOCKER, Severity.CRITICAL);
    private static final List<String> ESCAPED_ENVIRONMENTS = List.of("UAT", "PROD");
    private static final String STANDALONE_PREFIX = "standalone - ";
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private final ChangeRequestRepository changeRequestRepository;
    private final BugRepository bugRepository;
    private final TestCaseRepository testCaseRepository;

    public SprintReportService(ChangeRequestRepository changeRequestRepository,
                               BugRepository bugRepository,
                               TestCaseRepository testCaseRepository) {
        this.changeRequestRepository = changeRequestRepository;
        this.bugRepository = bugRepository;
        this.testCaseRepository = testCaseRepository;
    }

    @Transactional(readOnly = true)
    public List<SprintRow> rows(SprintSearchCriteria criteria) {
        Map<String, List<ChangeRequest>> crsBySprint = new LinkedHashMap<>();
        Map<String, List<Bug>> bugsBySprint = new LinkedHashMap<>();
        for (ChangeRequest cr : changeRequestRepository.findAll()) {
            crsBySprint.computeIfAbsent(groupKeyForCr(cr), key -> new ArrayList<>()).add(cr);
        }
        for (Bug bug : bugRepository.findAll()) {
            bugsBySprint.computeIfAbsent(groupKeyForBug(bug), key -> new ArrayList<>()).add(bug);
        }
        Map<Long, Map<TestCaseStatus, Long>> testCounts = testCountsByCr();

        TreeSet<String> sprints = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        sprints.addAll(crsBySprint.keySet());
        sprints.addAll(bugsBySprint.keySet());

        String filter = criteria.getSprint() == null ? "" : criteria.getSprint().trim().toLowerCase(Locale.ROOT);
        List<SprintRow> rows = new ArrayList<>();
        for (String sprint : sprints) {
            if (!filter.isEmpty() && !sprint.toLowerCase(Locale.ROOT).contains(filter)) {
                continue;
            }
            List<ChangeRequest> crs = crsBySprint.getOrDefault(sprint, List.of());
            List<Bug> bugs = bugsBySprint.getOrDefault(sprint, List.of());

            long totalTestCases = 0;
            long executedTestCases = 0;
            long passed = 0;
            long failed = 0;
            long blocked = 0;
            for (ChangeRequest cr : crs) {
                Map<TestCaseStatus, Long> counts = testCounts.getOrDefault(cr.getId(), Map.of());
                long total = counts.values().stream().mapToLong(Long::longValue).sum();
                totalTestCases += total;
                executedTestCases += total - counts.getOrDefault(TestCaseStatus.NOT_EXECUTED, 0L);
                passed += counts.getOrDefault(TestCaseStatus.PASS, 0L);
                failed += counts.getOrDefault(TestCaseStatus.FAIL, 0L);
                blocked += counts.getOrDefault(TestCaseStatus.BLOCKED, 0L);
            }
            long denominator = passed + failed + blocked;
            double passRate = denominator == 0 ? 0 : round(passed * 100.0 / denominator);

            long totalBugs = bugs.size();
            long openBugs = bugs.stream()
                    .filter(bug -> bug.getStatus() != null && !CLOSED_STATUSES.contains(bug.getStatus()))
                    .count();
            long criticalBugs = bugs.stream()
                    .filter(bug -> bug.getSeverity() != null && CRITICAL_SEVERITIES.contains(bug.getSeverity())
                            && bug.getStatus() != null && !CLOSED_STATUSES.contains(bug.getStatus()))
                    .count();
            long escapedDefects = bugs.stream()
                    .filter(bug -> bug.getEnvironment() != null
                            && ESCAPED_ENVIRONMENTS.contains(bug.getEnvironment().toUpperCase(Locale.ROOT)))
                    .count();

            rows.add(new SprintRow(sprint, crs.size(), totalTestCases, executedTestCases,
                    passed, failed, blocked, passRate, totalBugs, openBugs, criticalBugs, escapedDefects));
        }

        rows.sort(Comparator.comparing(SprintRow::sprint, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    @Transactional(readOnly = true)
    public Page<SprintRow> page(SprintSearchCriteria criteria, Pageable pageable) {
        List<SprintRow> rows = InMemorySort.apply(rows(criteria), pageable.getSort());
        int from = (int) Math.min(pageable.getOffset(), rows.size());
        int to = (int) Math.min(pageable.getOffset() + pageable.getPageSize(), rows.size());
        return new PageImpl<>(rows.subList(from, to), pageable, rows.size());
    }

    @Transactional(readOnly = true)
    public SprintSummary summary(SprintSearchCriteria criteria) {
        List<SprintRow> rows = rows(criteria);
        long changeRequests = 0;
        long totalTestCases = 0;
        long executedTestCases = 0;
        long passed = 0;
        long failed = 0;
        long blocked = 0;
        long totalBugs = 0;
        long openBugs = 0;
        long criticalBugs = 0;
        long escapedDefects = 0;
        for (SprintRow row : rows) {
            changeRequests += row.changeRequests();
            totalTestCases += row.totalTestCases();
            executedTestCases += row.executedTestCases();
            passed += row.passedTestCases();
            failed += row.failedTestCases();
            blocked += row.blockedTestCases();
            totalBugs += row.totalBugs();
            openBugs += row.openBugs();
            criticalBugs += row.criticalBugs();
            escapedDefects += row.escapedDefects();
        }
        long denominator = passed + failed + blocked;
        double overallPassRate = denominator == 0 ? 0 : round(passed * 100.0 / denominator);
        return new SprintSummary(rows.size(), changeRequests, totalTestCases, executedTestCases,
                totalBugs, openBugs, criticalBugs, escapedDefects, overallPassRate);
    }

    @Transactional(readOnly = true)
    public Page<SprintCrRow> changeRequestsForSprint(String sprint, Pageable pageable) {
        String key = sprint == null ? "" : sprint.trim();
        List<ChangeRequest> crs = changeRequestRepository.findAll().stream()
                .filter(cr -> groupKeyForCr(cr).equalsIgnoreCase(key))
                .sorted(Comparator.comparing(ChangeRequest::getCrNumber, Comparator.nullsLast(String::compareTo)))
                .toList();
        Map<Long, Map<TestCaseStatus, Long>> testCounts = testCountsByCr();
        List<SprintCrRow> rows = new ArrayList<>();
        for (ChangeRequest cr : crs) {
            Map<TestCaseStatus, Long> counts = testCounts.getOrDefault(cr.getId(), Map.of());
            long totalTestCases = counts.values().stream().mapToLong(Long::longValue).sum();
            long notExecuted = counts.getOrDefault(TestCaseStatus.NOT_EXECUTED, 0L);
            long passed = counts.getOrDefault(TestCaseStatus.PASS, 0L);
            long failed = counts.getOrDefault(TestCaseStatus.FAIL, 0L);
            long blocked = counts.getOrDefault(TestCaseStatus.BLOCKED, 0L);
            long executed = totalTestCases - notExecuted;
            long denominator = passed + failed + blocked;
            double passRate = denominator == 0 ? 0 : round(passed * 100.0 / denominator);

            long totalBugs = bugRepository.countByCrId(cr.getId());
            long openBugs = bugRepository.countByCrIdAndStatusNotIn(cr.getId(), CLOSED_STATUSES);
            long criticalBugs = bugRepository.countByCrIdAndSeverityInAndStatusNotIn(
                    cr.getId(), CRITICAL_SEVERITIES, CLOSED_STATUSES);
            long escapedDefects = bugRepository.countByCrIdAndEnvironmentIn(cr.getId(), ESCAPED_ENVIRONMENTS);

            rows.add(new SprintCrRow(
                    cr.getId(),
                    cr.getCrNumber(),
                    cr.getCrName(),
                    cr.getProject() == null ? "" : cr.getProject().getProjectCode(),
                    cr.getStatus(),
                    cr.getPriority(),
                    cr.getComplexity(),
                    totalTestCases,
                    executed,
                    passed,
                    failed,
                    blocked,
                    passRate,
                    totalBugs,
                    openBugs,
                    criticalBugs,
                    escapedDefects,
                    cr.getPlannedEndDate() == null ? "" : cr.getPlannedEndDate().toLocalDate().toString()));
        }
        List<SprintCrRow> sorted = InMemorySort.apply(rows, pageable.getSort());
        int from = (int) Math.min(pageable.getOffset(), sorted.size());
        int to = (int) Math.min(pageable.getOffset() + pageable.getPageSize(), sorted.size());
        return new PageImpl<>(sorted.subList(from, to), pageable, sorted.size());
    }

    private Map<Long, Map<TestCaseStatus, Long>> testCountsByCr() {
        Map<Long, Map<TestCaseStatus, Long>> result = new LinkedHashMap<>();
        for (Object[] row : testCaseRepository.countGroupByCrAndStatus()) {
            Long crId = (Long) row[0];
            TestCaseStatus status = (TestCaseStatus) row[1];
            Long count = (Long) row[2];
            result.computeIfAbsent(crId, key -> new LinkedHashMap<>()).put(status, count);
        }
        return result;
    }

    // Items with no sprint name are "standalone" and grouped by month (CR: planned end date, bug: reported date).
    private String groupKeyForCr(ChangeRequest cr) {
        if (cr.getSprintName() != null && !cr.getSprintName().isBlank()) {
            return cr.getSprintName().trim();
        }
        LocalDateTime date = cr.getPlannedEndDate() != null ? cr.getPlannedEndDate() : cr.getCreatedAt();
        return standaloneKey(date);
    }

    private String groupKeyForBug(Bug bug) {
        if (bug.getSprint() != null && !bug.getSprint().isBlank()) {
            return bug.getSprint().trim();
        }
        LocalDateTime date = bug.getReportedAt() != null ? bug.getReportedAt() : bug.getCreatedAt();
        return standaloneKey(date);
    }

    private String standaloneKey(LocalDateTime date) {
        return STANDALONE_PREFIX + (date == null ? "Unknown" : MONTH_FORMAT.format(date));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record SprintRow(
            String sprint,
            long changeRequests,
            long totalTestCases,
            long executedTestCases,
            long passedTestCases,
            long failedTestCases,
            long blockedTestCases,
            double passRate,
            long totalBugs,
            long openBugs,
            long criticalBugs,
            long escapedDefects) {
    }

    public record SprintSummary(
            long sprints,
            long changeRequests,
            long totalTestCases,
            long executedTestCases,
            long totalBugs,
            long openBugs,
            long criticalBugs,
            long escapedDefects,
            double overallPassRate) {
    }

    public record SprintCrRow(
            Long id,
            String crNumber,
            String crName,
            String project,
            CrStatus status,
            CrPriority priority,
            Complexity complexity,
            long totalTestCases,
            long executedTestCases,
            long passedTestCases,
            long failedTestCases,
            long blockedTestCases,
            double passRate,
            long totalBugs,
            long openBugs,
            long criticalBugs,
            long escapedDefects,
            String plannedEndDate) {
    }
}

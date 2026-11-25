package com.duc.crqa.report.service;

import com.duc.crqa.bug.repository.BugRepository;
import com.duc.crqa.common.enums.BugStatus;
import com.duc.crqa.common.enums.Role;
import com.duc.crqa.common.enums.Severity;
import com.duc.crqa.common.enums.TestCaseStatus;
import com.duc.crqa.common.util.InMemorySort;
import com.duc.crqa.report.dto.QaStatsSearchCriteria;
import com.duc.crqa.testcase.repository.TestCaseRepository;
import com.duc.crqa.user.entity.User;
import com.duc.crqa.user.service.UserService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QaStatisticsService {

    private static final List<BugStatus> CLOSED_STATUSES = List.of(BugStatus.CLOSED, BugStatus.REJECTED);
    private static final List<Severity> CRITICAL_SEVERITIES = List.of(Severity.BLOCKER, Severity.CRITICAL);
    private static final LocalDateTime MIN_DATE = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final LocalDateTime MAX_DATE = LocalDateTime.of(2999, 12, 31, 23, 59, 59);

    private final UserService userService;
    private final TestCaseRepository testCaseRepository;
    private final BugRepository bugRepository;

    public QaStatisticsService(UserService userService,
                               TestCaseRepository testCaseRepository,
                               BugRepository bugRepository) {
        this.userService = userService;
        this.testCaseRepository = testCaseRepository;
        this.bugRepository = bugRepository;
    }

    @Transactional(readOnly = true)
    public List<QaStatRow> rows(QaStatsSearchCriteria criteria) {
        return analyze(criteria);
    }

    @Transactional(readOnly = true)
    public Page<QaStatRow> page(QaStatsSearchCriteria criteria, Pageable pageable) {
        List<QaStatRow> rows = InMemorySort.apply(analyze(criteria), pageable.getSort());
        int from = (int) Math.min(pageable.getOffset(), rows.size());
        int to = (int) Math.min(pageable.getOffset() + pageable.getPageSize(), rows.size());
        return new PageImpl<>(rows.subList(from, to), pageable, rows.size());
    }

    @Transactional(readOnly = true)
    public QaStatSummary summary(QaStatsSearchCriteria criteria) {
        List<QaStatRow> rows = analyze(criteria);

        long executed = 0;
        long passed = 0;
        long failed = 0;
        long blocked = 0;
        long notApplicable = 0;
        long bugsRaised = 0;
        long openBugs = 0;
        long criticalBugs = 0;
        for (QaStatRow row : rows) {
            executed += row.executedTestCases();
            passed += row.passedTestCases();
            failed += row.failedTestCases();
            blocked += row.blockedTestCases();
            notApplicable += row.notApplicableTestCases();
            bugsRaised += row.bugsRaised();
            openBugs += row.openBugs();
            criticalBugs += row.criticalBugs();
        }

        long denominator = passed + failed + blocked;
        double overallPassRate = denominator == 0 ? 0 : round(passed * 100.0 / denominator);

        return new QaStatSummary(rows.size(), executed, passed, failed, blocked, notApplicable,
                overallPassRate, bugsRaised, openBugs, criticalBugs);
    }

    private List<QaStatRow> analyze(QaStatsSearchCriteria criteria) {
        LocalDateTime from = parseFrom(criteria.getFromDate());
        LocalDateTime to = parseTo(criteria.getToDate());

        Map<Long, Map<TestCaseStatus, Long>> testCounts = new LinkedHashMap<>();
        for (Object[] row : testCaseRepository.countByExecutorAndStatusBetween(from, to)) {
            Long userId = (Long) row[0];
            TestCaseStatus status = (TestCaseStatus) row[1];
            Long count = (Long) row[2];
            testCounts.computeIfAbsent(userId, key -> new LinkedHashMap<>()).merge(status, count, Long::sum);
        }

        Map<Long, BugTotals> bugTotals = new LinkedHashMap<>();
        for (Object[] row : bugRepository.countByReporterStatusSeverityBetween(from, to)) {
            Long userId = (Long) row[0];
            BugStatus status = (BugStatus) row[1];
            Severity severity = (Severity) row[2];
            long count = (Long) row[3];

            BugTotals totals = bugTotals.computeIfAbsent(userId, key -> new BugTotals());
            totals.total += count;
            boolean open = status != null && !CLOSED_STATUSES.contains(status);
            if (open) {
                totals.open += count;
                if (severity != null && CRITICAL_SEVERITIES.contains(severity)) {
                    totals.critical += count;
                }
            }
        }

        String filter = criteria.getSearch() == null ? "" : criteria.getSearch().trim().toLowerCase(Locale.ROOT);
        List<QaStatRow> rows = new ArrayList<>();
        for (User user : userService.listActive()) {
            if (!isQaMember(user)) {
                continue;
            }
            if (!filter.isEmpty() && !matches(user, filter)) {
                continue;
            }
            Map<TestCaseStatus, Long> counts = testCounts.getOrDefault(user.getId(), Map.of());
            long passed = counts.getOrDefault(TestCaseStatus.PASS, 0L);
            long failed = counts.getOrDefault(TestCaseStatus.FAIL, 0L);
            long blocked = counts.getOrDefault(TestCaseStatus.BLOCKED, 0L);
            long notApplicable = counts.getOrDefault(TestCaseStatus.NOT_APPLICABLE, 0L);
            long executed = passed + failed + blocked + notApplicable;
            long passDenominator = passed + failed + blocked;
            double passRate = passDenominator == 0 ? 0 : round(passed * 100.0 / passDenominator);

            BugTotals bugs = bugTotals.getOrDefault(user.getId(), new BugTotals());
            rows.add(new QaStatRow(
                    user.getId(),
                    user.getUsername(),
                    user.displayLabel(),
                    user.hasRole(Role.QA_LEAD) ? Role.QA_LEAD.name() : Role.QA_TESTER.name(),
                    executed,
                    passed,
                    failed,
                    blocked,
                    notApplicable,
                    passRate,
                    bugs.total,
                    bugs.open,
                    bugs.critical));
        }
        return rows;
    }

    private boolean isQaMember(User user) {
        return user.hasRole(Role.QA_LEAD) || user.hasRole(Role.QA_TESTER);
    }

    private boolean matches(User user, String filter) {
        return (user.getUsername() != null && user.getUsername().toLowerCase(Locale.ROOT).contains(filter))
                || (user.getDisplayName() != null
                        && user.getDisplayName().toLowerCase(Locale.ROOT).contains(filter));
    }

    private LocalDateTime parseFrom(String value) {
        LocalDate date = parseDate(value);
        return date == null ? MIN_DATE : date.atStartOfDay();
    }

    private LocalDateTime parseTo(String value) {
        LocalDate date = parseDate(value);
        return date == null ? MAX_DATE : date.atTime(23, 59, 59);
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static final class BugTotals {
        private long total;
        private long open;
        private long critical;
    }

    public record QaStatRow(
            Long userId,
            String username,
            String displayName,
            String role,
            long executedTestCases,
            long passedTestCases,
            long failedTestCases,
            long blockedTestCases,
            long notApplicableTestCases,
            double passRate,
            long bugsRaised,
            long openBugs,
            long criticalBugs) {
    }

    public record QaStatSummary(
            long qaMembers,
            long executedTestCases,
            long passedTestCases,
            long failedTestCases,
            long blockedTestCases,
            long notApplicableTestCases,
            double overallPassRate,
            long bugsRaised,
            long openBugs,
            long criticalBugs) {
    }
}

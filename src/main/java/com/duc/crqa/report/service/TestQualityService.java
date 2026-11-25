package com.duc.crqa.report.service;

import com.duc.crqa.bug.repository.BugRepository;
import com.duc.crqa.common.enums.BugStatus;
import com.duc.crqa.common.enums.CrStatus;
import com.duc.crqa.common.enums.Severity;
import com.duc.crqa.common.enums.TestCaseStatus;
import com.duc.crqa.cr.dto.CrSearchCriteria;
import com.duc.crqa.cr.entity.ChangeRequest;
import com.duc.crqa.cr.service.ChangeRequestService;
import com.duc.crqa.common.util.InMemorySort;
import com.duc.crqa.testcase.repository.TestCaseRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TestQualityService {

    private static final List<BugStatus> CLOSED_STATUSES = List.of(BugStatus.CLOSED, BugStatus.REJECTED);
    private static final List<Severity> CRITICAL_SEVERITIES = List.of(Severity.BLOCKER, Severity.CRITICAL);
    private static final List<String> ESCAPED_ENVIRONMENTS = List.of("UAT", "PROD");

    private final ChangeRequestService changeRequestService;
    private final TestCaseRepository testCaseRepository;
    private final BugRepository bugRepository;

    public TestQualityService(ChangeRequestService changeRequestService,
                              TestCaseRepository testCaseRepository,
                              BugRepository bugRepository) {
        this.changeRequestService = changeRequestService;
        this.testCaseRepository = testCaseRepository;
        this.bugRepository = bugRepository;
    }

    @Transactional(readOnly = true)
    public List<CrQualityRow> rows(CrSearchCriteria criteria) {
        return analyze(criteria).rows;
    }

    @Transactional(readOnly = true)
    public Page<CrQualityRow> page(CrSearchCriteria criteria, Pageable pageable) {
        List<CrQualityRow> rows = InMemorySort.apply(analyze(criteria).rows, pageable.getSort());
        int from = (int) Math.min(pageable.getOffset(), rows.size());
        int to = (int) Math.min(pageable.getOffset() + pageable.getPageSize(), rows.size());
        return new PageImpl<>(rows.subList(from, to), pageable, rows.size());
    }

    @Transactional(readOnly = true)
    public QualitySummary summary(CrSearchCriteria criteria) {
        ReportData data = analyze(criteria);
        List<CrQualityRow> rows = data.rows;

        long totalTestCases = 0;
        long executedTestCases = 0;
        long passedTestCases = 0;
        long failedTestCases = 0;
        long blockedTestCases = 0;
        long notApplicableTestCases = 0;
        long totalBugs = 0;
        long openBugs = 0;
        long criticalBugs = 0;
        long escapedDefects = 0;
        long qaCycleDaysSum = 0;
        int qaCycleDaysCount = 0;

        for (CrQualityRow row : rows) {
            totalTestCases += row.totalTestCases();
            executedTestCases += row.executedTestCases();
            passedTestCases += row.passedTestCases();
            failedTestCases += row.failedTestCases();
            blockedTestCases += row.blockedTestCases();
            notApplicableTestCases += row.notApplicableTestCases();
            totalBugs += row.totalBugs();
            openBugs += row.openBugs();
            criticalBugs += row.criticalBugs();
            escapedDefects += row.escapedDefects();
            if (row.qaCycleDays() > 0) {
                qaCycleDaysSum += row.qaCycleDays();
                qaCycleDaysCount++;
            }
        }

        long denominator = passedTestCases + failedTestCases + blockedTestCases;
        double overallPassRate = denominator == 0 ? 0 : round(passedTestCases * 100.0 / denominator);
        double overallDefectDensity = totalTestCases == 0 ? 0 : round(totalBugs * 1.0 / totalTestCases);
        long averageQaCycleDays = qaCycleDaysCount == 0 ? 0 : qaCycleDaysSum / qaCycleDaysCount;

        return new QualitySummary(
                totalTestCases,
                executedTestCases,
                passedTestCases,
                failedTestCases,
                blockedTestCases,
                notApplicableTestCases,
                overallPassRate,
                totalBugs,
                openBugs,
                criticalBugs,
                escapedDefects,
                overallDefectDensity,
                averageQaCycleDays,
                data.testStatusCounts);
    }

    private ReportData analyze(CrSearchCriteria criteria) {
        List<ChangeRequest> crs = changeRequestService.search(criteria, Pageable.unpaged()).getContent();
        List<CrQualityRow> rows = new ArrayList<>();
        Map<TestCaseStatus, Long> testStatusCounts = new LinkedHashMap<>();
        for (TestCaseStatus status : TestCaseStatus.values()) {
            testStatusCounts.put(status, 0L);
        }

        for (ChangeRequest cr : crs) {
            Map<TestCaseStatus, Long> perCrStatusCounts = testStatusCounts(cr.getId());
            rows.add(toRow(cr, perCrStatusCounts));
            perCrStatusCounts.forEach((status, count) -> testStatusCounts.merge(status, count, Long::sum));
        }

        return new ReportData(rows, testStatusCounts);
    }

    private Map<TestCaseStatus, Long> testStatusCounts(Long crId) {
        Map<TestCaseStatus, Long> counts = new LinkedHashMap<>();
        for (TestCaseStatus status : TestCaseStatus.values()) {
            counts.put(status, 0L);
        }
        for (Object[] row : testCaseRepository.countGroupByStatusForCr(crId)) {
            counts.put((TestCaseStatus) row[0], (Long) row[1]);
        }
        return counts;
    }

    private CrQualityRow toRow(ChangeRequest cr, Map<TestCaseStatus, Long> statusCounts) {
        long totalTestCases = statusCounts.values().stream().mapToLong(Long::longValue).sum();
        long notExecuted = statusCounts.get(TestCaseStatus.NOT_EXECUTED);
        long passed = statusCounts.get(TestCaseStatus.PASS);
        long failed = statusCounts.get(TestCaseStatus.FAIL);
        long blocked = statusCounts.get(TestCaseStatus.BLOCKED);
        long notApplicable = statusCounts.get(TestCaseStatus.NOT_APPLICABLE);
        long executed = totalTestCases - notExecuted;

        long passDenominator = passed + failed + blocked;
        double passRate = passDenominator == 0 ? 0 : round(passed * 100.0 / passDenominator);

        long totalBugs = bugRepository.countByCrId(cr.getId());
        long openBugs = bugRepository.countByCrIdAndStatusNotIn(cr.getId(), CLOSED_STATUSES);
        long criticalBugs = bugRepository.countByCrIdAndSeverityInAndStatusNotIn(
                cr.getId(), CRITICAL_SEVERITIES, CLOSED_STATUSES);
        long escapedDefects = bugRepository.countByCrIdAndEnvironmentIn(cr.getId(), ESCAPED_ENVIRONMENTS);
        double defectDensity = totalTestCases == 0 ? 0 : round(totalBugs * 1.0 / totalTestCases);

        long qaCycleDays = qaCycleDays(cr);
        String plannedEndDate = cr.getPlannedEndDate() == null
                ? "" : cr.getPlannedEndDate().toLocalDate().toString();
        boolean overdue = cr.getPlannedEndDate() != null
                && cr.getPlannedEndDate().isBefore(LocalDateTime.now())
                && !cr.getStatus().equals(CrStatus.COMPLETED)
                && !cr.getStatus().equals(CrStatus.CANCELLED);

        return new CrQualityRow(
                cr.getId(),
                cr.getCrNumber(),
                cr.getCrName(),
                cr.getProject() == null ? "" : cr.getProject().getProjectCode(),
                cr.getStatus(),
                totalTestCases,
                executed,
                passed,
                failed,
                blocked,
                notApplicable,
                passRate,
                totalBugs,
                openBugs,
                criticalBugs,
                escapedDefects,
                defectDensity,
                qaCycleDays,
                plannedEndDate,
                overdue);
    }

    private long qaCycleDays(ChangeRequest cr) {
        if (cr.getActualStartDate() == null) {
            return 0;
        }
        LocalDateTime end = cr.getActualEndDate() != null ? cr.getActualEndDate() : LocalDateTime.now();
        return Math.max(0, ChronoUnit.DAYS.between(cr.getActualStartDate(), end));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record ReportData(List<CrQualityRow> rows, Map<TestCaseStatus, Long> testStatusCounts) {
    }

    public record CrQualityRow(
            Long id,
            String crNumber,
            String crName,
            String project,
            CrStatus status,
            long totalTestCases,
            long executedTestCases,
            long passedTestCases,
            long failedTestCases,
            long blockedTestCases,
            long notApplicableTestCases,
            double passRate,
            long totalBugs,
            long openBugs,
            long criticalBugs,
            long escapedDefects,
            double defectDensity,
            long qaCycleDays,
            String plannedEndDate,
            boolean overdue) {
    }

    public record QualitySummary(
            long totalTestCases,
            long executedTestCases,
            long passedTestCases,
            long failedTestCases,
            long blockedTestCases,
            long notApplicableTestCases,
            double overallPassRate,
            long totalBugs,
            long openBugs,
            long criticalBugs,
            long escapedDefects,
            double overallDefectDensity,
            long averageQaCycleDays,
            Map<TestCaseStatus, Long> testStatusCounts) {
    }
}

package com.duc.crqa.analytics.service;

import com.duc.crqa.analytics.dto.AnalyticsSearchCriteria;
import com.duc.crqa.analytics.repository.UserActivityRepository;
import com.duc.crqa.audit.repository.AuditLogRepository;
import com.duc.crqa.bug.entity.Bug;
import com.duc.crqa.bug.repository.BugRepository;
import com.duc.crqa.common.enums.BugStatus;
import com.duc.crqa.common.enums.Role;
import com.duc.crqa.common.enums.Severity;
import com.duc.crqa.common.enums.TestCaseStatus;
import com.duc.crqa.common.util.InMemorySort;
import com.duc.crqa.cr.entity.ChangeRequest;
import com.duc.crqa.cr.repository.ChangeRequestRepository;
import com.duc.crqa.testcase.repository.TestCaseRepository;
import com.duc.crqa.user.entity.User;
import com.duc.crqa.user.service.UserService;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {

    private static final LocalDateTime MIN_DATE = LocalDateTime.of(1900, 1, 1, 0, 0);
    private static final LocalDateTime MAX_DATE = LocalDateTime.of(2999, 12, 31, 23, 59, 59);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Set<BugStatus> CLOSED_BUG_STATUSES = Set.of(BugStatus.CLOSED, BugStatus.REJECTED);
    private static final Set<Severity> CRITICAL_SEVERITIES = Set.of(Severity.BLOCKER, Severity.CRITICAL);

    private final AuditLogRepository auditLogRepository;
    private final UserActivityRepository userActivityRepository;
    private final BugRepository bugRepository;
    private final ChangeRequestRepository changeRequestRepository;
    private final TestCaseRepository testCaseRepository;
    private final UserService userService;

    public AnalyticsService(AuditLogRepository auditLogRepository,
                            UserActivityRepository userActivityRepository,
                            BugRepository bugRepository,
                            ChangeRequestRepository changeRequestRepository,
                            TestCaseRepository testCaseRepository,
                            UserService userService) {
        this.auditLogRepository = auditLogRepository;
        this.userActivityRepository = userActivityRepository;
        this.bugRepository = bugRepository;
        this.changeRequestRepository = changeRequestRepository;
        this.testCaseRepository = testCaseRepository;
        this.userService = userService;
    }

    // ---------------------------------------------------------------- user activity

    @Transactional(readOnly = true)
    public List<UserActivityRow> userActivity(AnalyticsSearchCriteria criteria) {
        Map<String, Map<String, Long>> audit = auditCounts(criteria);
        Map<Long, Long> timeByUser = new LinkedHashMap<>();
        for (TimeSpentRow row : timeSpent(criteria)) {
            timeByUser.merge(row.userId(), row.totalSeconds(), Long::sum);
        }
        String filter = normalize(criteria.getSearch());
        List<UserActivityRow> rows = new ArrayList<>();
        for (User user : userService.listActive()) {
            if (criteria.getUserId() != null && !criteria.getUserId().equals(user.getId())) {
                continue;
            }
            if (!matches(filter, user.getUsername(), user.getDisplayName())) {
                continue;
            }
            Map<String, Long> counts = audit.getOrDefault(user.getUsername(), Map.of());
            rows.add(new UserActivityRow(
                    user.getId(), user.getUsername(), user.displayLabel(), roleLabel(user),
                    counts.getOrDefault("LOGIN", 0L),
                    counts.getOrDefault("LOGOUT", 0L),
                    counts.getOrDefault("CR_CREATED", 0L),
                    counts.getOrDefault("CR_UPDATED", 0L),
                    counts.getOrDefault("TEST_CASE_EXECUTED", 0L),
                    counts.getOrDefault("BUG_CREATED", 0L),
                    timeByUser.getOrDefault(user.getId(), 0L)));
        }
        return rows;
    }

    private Map<String, Map<String, Long>> auditCounts(AnalyticsSearchCriteria criteria) {
        Map<String, Map<String, Long>> result = new LinkedHashMap<>();
        for (Object[] row : auditLogRepository.countByUserAndActionBetween(from(criteria), to(criteria))) {
            String username = row[0] == null ? "" : row[0].toString();
            String action = row[1] == null ? "" : row[1].toString();
            long count = ((Number) row[2]).longValue();
            result.computeIfAbsent(username, key -> new LinkedHashMap<>()).merge(action, count, Long::sum);
        }
        return result;
    }

    // ---------------------------------------------------------------- bugs by group

    @Transactional(readOnly = true)
    public List<BugGroupRow> bugGroups(AnalyticsSearchCriteria criteria) {
        String groupBy = criteria.getGroupBy() == null ? "cr" : criteria.getGroupBy();
        String filter = normalize(criteria.getSearch());
        Map<String, List<Bug>> groups = new LinkedHashMap<>();
        for (Bug bug : filteredBugs(criteria)) {
            if (criteria.getUserId() != null && "developer".equals(groupBy)
                    && (bug.getAssignedTo() == null || !criteria.getUserId().equals(bug.getAssignedTo().getId()))) {
                continue;
            }
            String key = groupKey(bug, groupBy);
            if (!matches(filter, key)) {
                continue;
            }
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(bug);
        }
        List<BugGroupRow> rows = new ArrayList<>();
        for (Map.Entry<String, List<Bug>> entry : groups.entrySet()) {
            List<Bug> bugs = entry.getValue();
            long total = bugs.size();
            long open = bugs.stream().filter(this::isOpen).count();
            long critical = bugs.stream()
                    .filter(bug -> bug.getSeverity() != null && CRITICAL_SEVERITIES.contains(bug.getSeverity()))
                    .count();
            double avgFixHours = bugs.stream()
                    .filter(bug -> bug.getClosedAt() != null && bug.getReportedAt() != null)
                    .mapToLong(bug -> Duration.between(bug.getReportedAt(), bug.getClosedAt()).toMinutes())
                    .average()
                    .orElse(0);
            rows.add(new BugGroupRow(entry.getKey(), total, open, critical, round(avgFixHours / 60.0)));
        }
        return rows;
    }

    private String groupKey(Bug bug, String groupBy) {
        return switch (groupBy) {
            case "testCase" -> bug.getTestCase() == null
                    ? "(no test case)"
                    : bug.getTestCase().getTestCaseNumber() + " - " + nullSafe(bug.getTestCase().getTitle());
            case "developer" -> bug.getAssignedTo() == null ? "(unassigned)" : bug.getAssignedTo().displayLabel();
            default -> bug.getCr() == null
                    ? "(no change request)"
                    : bug.getCr().getCrNumber() + " - " + nullSafe(bug.getCr().getCrName());
        };
    }

    // ---------------------------------------------------------------- test execution

    @Transactional(readOnly = true)
    public List<ExecutionRow> execution(AnalyticsSearchCriteria criteria) {
        Map<Long, User> users = usersById();
        Map<Long, Map<TestCaseStatus, Long>> counts = new LinkedHashMap<>();
        for (Object[] row : testCaseRepository.countByExecutorAndStatusBetween(from(criteria), to(criteria))) {
            Long userId = ((Number) row[0]).longValue();
            TestCaseStatus status = (TestCaseStatus) row[1];
            long count = ((Number) row[2]).longValue();
            counts.computeIfAbsent(userId, key -> new LinkedHashMap<>()).merge(status, count, Long::sum);
        }
        String filter = normalize(criteria.getSearch());
        List<ExecutionRow> rows = new ArrayList<>();
        for (Map.Entry<Long, Map<TestCaseStatus, Long>> entry : counts.entrySet()) {
            User user = users.get(entry.getKey());
            String member = user == null ? "User #" + entry.getKey() : user.displayLabel();
            String username = user == null ? "" : user.getUsername();
            if (criteria.getUserId() != null && !criteria.getUserId().equals(entry.getKey())) {
                continue;
            }
            if (!matches(filter, member, username)) {
                continue;
            }
            Map<TestCaseStatus, Long> statusCounts = entry.getValue();
            long passed = statusCounts.getOrDefault(TestCaseStatus.PASS, 0L);
            long failed = statusCounts.getOrDefault(TestCaseStatus.FAIL, 0L);
            long blocked = statusCounts.getOrDefault(TestCaseStatus.BLOCKED, 0L);
            long notApplicable = statusCounts.getOrDefault(TestCaseStatus.NOT_APPLICABLE, 0L);
            long executed = statusCounts.values().stream().mapToLong(Long::longValue).sum();
            double passRate = executed == 0 ? 0 : round(passed * 100.0 / executed);
            rows.add(new ExecutionRow(entry.getKey(), username, member, executed, passed, failed, blocked,
                    notApplicable, passRate));
        }
        return rows;
    }

    // ---------------------------------------------------------------- defects

    @Transactional(readOnly = true)
    public List<DefectCountRow> defectsBySeverity(AnalyticsSearchCriteria criteria) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Bug bug : filteredBugs(criteria)) {
            counts.merge(bug.getSeverity() == null ? "Unspecified" : bug.getSeverity().name(), 1L, Long::sum);
        }
        return toDefectRows(counts);
    }

    @Transactional(readOnly = true)
    public List<DefectCountRow> defectsByStatus(AnalyticsSearchCriteria criteria) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Bug bug : filteredBugs(criteria)) {
            counts.merge(bug.getStatus() == null ? "Unspecified" : bug.getStatus().name(), 1L, Long::sum);
        }
        return toDefectRows(counts);
    }

    private List<DefectCountRow> toDefectRows(Map<String, Long> counts) {
        List<DefectCountRow> rows = new ArrayList<>();
        counts.forEach((label, count) -> rows.add(new DefectCountRow(label, count)));
        return rows;
    }

    // ---------------------------------------------------------------- time spent

    @Transactional(readOnly = true)
    public List<TimeSpentRow> timeSpent(AnalyticsSearchCriteria criteria) {
        Map<Long, User> users = usersById();
        String filter = normalize(criteria.getSearch());
        List<TimeSpentRow> rows = new ArrayList<>();
        for (Object[] row : userActivityRepository.sumByUserAndFeatureBetween(from(criteria), to(criteria))) {
            Long userId = ((Number) row[0]).longValue();
            String feature = row[1] == null ? "Other" : row[1].toString();
            long seconds = row[2] == null ? 0 : ((Number) row[2]).longValue();
            long sessions = row[3] == null ? 0 : ((Number) row[3]).longValue();
            User user = users.get(userId);
            String member = user == null ? "User #" + userId : user.displayLabel();
            if (criteria.getUserId() != null && !criteria.getUserId().equals(userId)) {
                continue;
            }
            if (!matches(filter, member, feature)) {
                continue;
            }
            rows.add(new TimeSpentRow(userId, member, feature, seconds, sessions));
        }
        return rows;
    }

    // ---------------------------------------------------------------- CR cycle time

    @Transactional(readOnly = true)
    public List<CrCycleRow> crCycle(AnalyticsSearchCriteria criteria) {
        LocalDateTime from = from(criteria);
        LocalDateTime to = to(criteria);
        String filter = normalize(criteria.getSearch());
        List<CrCycleRow> rows = new ArrayList<>();
        for (ChangeRequest cr : changeRequestRepository.findAll()) {
            LocalDateTime created = cr.getCreatedAt();
            if (created == null || created.isBefore(from) || created.isAfter(to)) {
                continue;
            }
            if (!matches(filter, cr.getCrNumber(), cr.getCrName())) {
                continue;
            }
            LocalDateTime completed = cr.getActualEndDate();
            Long cycleDays = completed == null ? null : Duration.between(created, completed).toDays();
            rows.add(new CrCycleRow(
                    cr.getId(), cr.getCrNumber(), cr.getCrName(),
                    cr.getProject() == null ? "" : cr.getProject().getProjectCode(),
                    cr.getStatus() == null ? "" : cr.getStatus().name(),
                    created.format(DATE),
                    completed == null ? "" : completed.format(DATE),
                    cycleDays));
        }
        return rows;
    }

    // ---------------------------------------------------------------- helpers

    public static <T> Page<T> page(List<T> rows, Pageable pageable) {
        List<T> sorted = InMemorySort.apply(rows, pageable.getSort());
        int from = (int) Math.min(pageable.getOffset(), sorted.size());
        int to = (int) Math.min(pageable.getOffset() + pageable.getPageSize(), sorted.size());
        return new PageImpl<>(sorted.subList(from, to), pageable, sorted.size());
    }

    private List<Bug> filteredBugs(AnalyticsSearchCriteria criteria) {
        LocalDateTime from = from(criteria);
        LocalDateTime to = to(criteria);
        List<Bug> bugs = new ArrayList<>();
        for (Bug bug : bugRepository.findAll()) {
            if (bug.getReportedAt() == null || bug.getReportedAt().isBefore(from) || bug.getReportedAt().isAfter(to)) {
                continue;
            }
            bugs.add(bug);
        }
        return bugs;
    }

    private Map<Long, User> usersById() {
        Map<Long, User> users = new LinkedHashMap<>();
        for (User user : userService.listActive()) {
            users.put(user.getId(), user);
        }
        return users;
    }

    private boolean isOpen(Bug bug) {
        return bug.getStatus() != null && !CLOSED_BUG_STATUSES.contains(bug.getStatus());
    }

    private String roleLabel(User user) {
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            return "";
        }
        Set<String> names = new TreeSet<>();
        for (Role role : user.getRoles()) {
            names.add(role.name());
        }
        return String.join("/", names);
    }

    private boolean matches(String filter, String... values) {
        if (filter.isEmpty()) {
            return true;
        }
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(filter)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private LocalDateTime from(AnalyticsSearchCriteria criteria) {
        LocalDate date = parseDate(criteria.getFromDate());
        return date == null ? MIN_DATE : date.atStartOfDay();
    }

    private LocalDateTime to(AnalyticsSearchCriteria criteria) {
        LocalDate date = parseDate(criteria.getToDate());
        return date == null ? MAX_DATE : date.atTime(23, 59, 59);
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    // ---------------------------------------------------------------- row records

    public record UserActivityRow(Long userId, String username, String member, String roles,
                                  long logins, long logouts, long crCreated, long crUpdated,
                                  long tcExecuted, long bugsRaised, long timeSpentSeconds) {
    }

    public record BugGroupRow(String group, long total, long open, long critical, double avgFixHours) {
    }

    public record ExecutionRow(Long userId, String username, String member, long executed, long passed,
                               long failed, long blocked, long notApplicable, double passRate) {
    }

    public record DefectCountRow(String label, long count) {
    }

    public record TimeSpentRow(Long userId, String member, String feature, long totalSeconds, long sessions) {
    }

    public record CrCycleRow(Long id, String crNumber, String crName, String project, String status,
                             String created, String completed, Long cycleDays) {
    }
}

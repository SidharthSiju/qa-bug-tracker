package com.duc.crqa.report.service;

import com.duc.crqa.bug.entity.Bug;
import com.duc.crqa.bug.service.BugService;
import com.duc.crqa.common.enums.SlaStatus;
import com.duc.crqa.common.util.SlaService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aging report: how much of the open defect backlog is past (or close to) its SLA, broken down by severity, assignee
 * and change request.
 */
@Service
public class SlaReportService {

    private final BugService bugService;
    private final SlaService slaService;

    public SlaReportService(BugService bugService, SlaService slaService) {
        this.bugService = bugService;
        this.slaService = slaService;
    }

    /** One line of the report: the group, how much of it is late, and how long the worst offender has been open. */
    public record SlaGroupRow(String group, long total, long breached, long approaching, long oldestAgeDays) {
    }

    public record SlaReport(long openBugs, long breached, long approaching,
                            List<SlaGroupRow> bySeverity, List<SlaGroupRow> byAssignee,
                            List<SlaGroupRow> byChangeRequest) {
    }

    @Transactional(readOnly = true)
    public SlaReport build() {
        List<Bug> open = bugService.listOpen();
        long breached = count(open, SlaStatus.BREACHED);
        long approaching = count(open, SlaStatus.APPROACHING);

        return new SlaReport(open.size(), breached, approaching,
                group(open, bug -> bug.getSeverity() == null ? "(none)" : bug.getSeverity().name()),
                group(open, bug -> bug.getAssignedTo() == null ? "(unassigned)" : bug.getAssignedTo().displayLabel()),
                group(open, bug -> bug.getCr() == null ? "(no change request)" : bug.getCr().getCrNumber()));
    }

    private long count(List<Bug> bugs, SlaStatus sla) {
        return bugs.stream().filter(bug -> slaService.status(bug) == sla).count();
    }

    private List<SlaGroupRow> group(List<Bug> bugs, Function<Bug, String> key) {
        Map<String, List<Bug>> grouped = new TreeMap<>();
        for (Bug bug : bugs) {
            grouped.computeIfAbsent(key.apply(bug), name -> new ArrayList<>()).add(bug);
        }

        List<SlaGroupRow> rows = new ArrayList<>();
        grouped.forEach((name, group) -> rows.add(new SlaGroupRow(name, group.size(),
                count(group, SlaStatus.BREACHED), count(group, SlaStatus.APPROACHING),
                group.stream().mapToLong(this::ageDays).max().orElse(0))));

        // Worst first: most breaching, then largest group.
        rows.sort(Comparator.comparingLong(SlaGroupRow::breached).reversed()
                .thenComparing(Comparator.comparingLong(SlaGroupRow::total).reversed()));
        return rows;
    }

    private long ageDays(Bug bug) {
        if (bug.getReportedAt() == null) {
            return 0;
        }
        return Math.max(0, Duration.between(bug.getReportedAt(), LocalDateTime.now()).toDays());
    }
}

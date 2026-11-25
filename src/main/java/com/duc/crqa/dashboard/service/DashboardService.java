package com.duc.crqa.dashboard.service;

import com.duc.crqa.bug.entity.Bug;
import com.duc.crqa.bug.repository.BugRepository;
import com.duc.crqa.common.enums.BugStatus;
import com.duc.crqa.common.enums.CrStatus;
import com.duc.crqa.common.enums.Severity;
import com.duc.crqa.bug.service.BugService;
import com.duc.crqa.common.enums.SlaStatus;
import com.duc.crqa.common.util.InMemorySort;
import com.duc.crqa.cr.entity.ChangeRequest;
import com.duc.crqa.cr.repository.ChangeRequestRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private static final List<BugStatus> CLOSED_STATUSES = List.of(BugStatus.CLOSED, BugStatus.REJECTED);
    private static final List<CrStatus> ACTIVE_CR_STATUSES = List.of(
            CrStatus.CREATED, CrStatus.DEVELOPMENT, CrStatus.READY_FOR_QA,
            CrStatus.QA_IN_PROGRESS, CrStatus.QA_BLOCKED, CrStatus.QA_COMPLETED, CrStatus.UAT);

    private final ChangeRequestRepository changeRequestRepository;
    private final BugRepository bugRepository;
    private final BugService bugService;

    public DashboardService(ChangeRequestRepository changeRequestRepository,
                            BugRepository bugRepository,
                            BugService bugService) {
        this.changeRequestRepository = changeRequestRepository;
        this.bugRepository = bugRepository;
        this.bugService = bugService;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> kpis() {
        Map<String, Long> kpis = new LinkedHashMap<>();
        kpis.put("activeCrs", changeRequestRepository.countByStatusIn(ACTIVE_CR_STATUSES));
        kpis.put("crsInQa", changeRequestRepository.countByStatusIn(
                List.of(CrStatus.QA_IN_PROGRESS, CrStatus.QA_BLOCKED)));
        kpis.put("openBugs", bugRepository.countByStatusNotIn(CLOSED_STATUSES));
        kpis.put("criticalBugs", bugRepository.countBySeverityInAndStatusNotIn(
                List.of(Severity.BLOCKER, Severity.CRITICAL), CLOSED_STATUSES));
        kpis.put("blockedCrs", changeRequestRepository.countByStatus(CrStatus.QA_BLOCKED));
        kpis.put("overdueBugs", bugService.countBySla(SlaStatus.BREACHED));
        kpis.put("approachingBugs", bugService.countBySla(SlaStatus.APPROACHING));
        return kpis;
    }

    @Transactional(readOnly = true)
    public Map<BugStatus, Long> bugStatusCounts() {
        Map<BugStatus, Long> counts = new LinkedHashMap<>();
        for (BugStatus status : BugStatus.values()) {
            counts.put(status, 0L);
        }
        for (Object[] row : bugRepository.countGroupByStatus()) {
            counts.put((BugStatus) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public Map<Severity, Long> bugSeverityCounts() {
        Map<Severity, Long> counts = new LinkedHashMap<>();
        for (Severity severity : Severity.values()) {
            counts.put(severity, 0L);
        }
        for (Object[] row : bugRepository.countGroupBySeverity()) {
            counts.put((Severity) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public Map<CrStatus, Long> crStatusCounts() {
        Map<CrStatus, Long> counts = new LinkedHashMap<>();
        for (CrStatus status : CrStatus.values()) {
            counts.put(status, 0L);
        }
        for (Object[] row : changeRequestRepository.countGroupByStatus()) {
            counts.put((CrStatus) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public Page<CrProgressRow> crProgress(Pageable pageable) {
        List<CrProgressRow> rows = changeRequestRepository
                .findByStatusInOrderByCreatedAtDesc(ACTIVE_CR_STATUSES, Pageable.unpaged())
                .map(this::toProgressRow)
                .getContent();
        List<CrProgressRow> sorted = InMemorySort.apply(rows, pageable.getSort());
        int from = (int) Math.min(pageable.getOffset(), sorted.size());
        int to = (int) Math.min(pageable.getOffset() + pageable.getPageSize(), sorted.size());
        return new PageImpl<>(sorted.subList(from, to), pageable, sorted.size());
    }

    public record CrProgressRow(
            Long id,
            String crNumber,
            String crName,
            String project,
            String qaLead,
            long totalBugs,
            long openBugs,
            long criticalBugs,
            long retestingBugs,
            long closedBugs,
            CrStatus status,
            String plannedEndDate,
            boolean overdue) {
    }

    private CrProgressRow toProgressRow(ChangeRequest cr) {
        long total = bugRepository.countByCrId(cr.getId());
        long open = bugRepository.countByCrIdAndStatusNotIn(cr.getId(), CLOSED_STATUSES);
        long critical = bugRepository.countByCrIdAndSeverityInAndStatusNotIn(cr.getId(),
                List.of(Severity.BLOCKER, Severity.CRITICAL), CLOSED_STATUSES);
        long retesting = bugRepository.countByCrIdAndStatus(cr.getId(), BugStatus.RETESTING);
        long closed = bugRepository.countByCrIdAndStatusIn(cr.getId(), CLOSED_STATUSES);
        boolean overdue = cr.getPlannedEndDate() != null
                && cr.getPlannedEndDate().isBefore(java.time.LocalDateTime.now())
                && !cr.getStatus().equals(CrStatus.COMPLETED);

        return new CrProgressRow(
                cr.getId(),
                cr.getCrNumber(),
                cr.getCrName(),
                cr.getProject() == null ? "" : cr.getProject().getProjectCode(),
                cr.getQaLead() == null ? "" : cr.getQaLead().displayLabel(),
                total,
                open,
                critical,
                retesting,
                closed,
                cr.getStatus(),
                cr.getPlannedEndDate() == null ? "" : cr.getPlannedEndDate().toLocalDate().toString(),
                overdue);
    }
}

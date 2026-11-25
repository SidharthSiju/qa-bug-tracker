package com.duc.crqa.bug.repository;

import com.duc.crqa.bug.entity.Bug;
import com.duc.crqa.common.enums.BugStatus;
import com.duc.crqa.common.enums.Severity;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BugRepository extends JpaRepository<Bug, Long>, JpaSpecificationExecutor<Bug> {

    long countByStatusNotIn(List<BugStatus> statuses);

    long countBySeverityInAndStatusNotIn(List<Severity> severities, List<BugStatus> statuses);

    long countByCrId(Long crId);

    long countByCrIdAndStatusNotIn(Long crId, List<BugStatus> statuses);

    long countByCrIdAndSeverityInAndStatusNotIn(Long crId, List<Severity> severities, List<BugStatus> statuses);

    long countByCrIdAndStatusIn(Long crId, List<BugStatus> statuses);

    long countByCrIdAndStatus(Long crId, BugStatus status);

    long countByCrIdAndEnvironmentIn(Long crId, List<String> environments);

    long countByBugNumberStartingWith(String prefix);

    List<Bug> findByStatusNotIn(List<BugStatus> statuses);

    List<Bug> findByCrIdOrderByIdDesc(Long crId);

    List<Bug> findByAssignedToIdAndStatusNotIn(Long userId, List<BugStatus> statuses);

    @Query("select b.status, count(b) from Bug b group by b.status")
    List<Object[]> countGroupByStatus();

    @Query("select b.severity, count(b) from Bug b group by b.severity")
    List<Object[]> countGroupBySeverity();

    @Query("select b.cr.id, count(b) from Bug b group by b.cr.id")
    List<Object[]> countGroupByCr();

    @Query("select b.reportedBy.id, b.status, b.severity, count(b) from Bug b "
            + "where b.reportedAt >= :from and b.reportedAt <= :to "
            + "group by b.reportedBy.id, b.status, b.severity")
    List<Object[]> countByReporterStatusSeverityBetween(@Param("from") LocalDateTime from,
                                                        @Param("to") LocalDateTime to);
}

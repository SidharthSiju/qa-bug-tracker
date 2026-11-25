package com.duc.crqa.testcase.repository;

import com.duc.crqa.testcase.entity.TestCase;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TestCaseRepository extends JpaRepository<TestCase, Long>, JpaSpecificationExecutor<TestCase> {

    List<TestCase> findByCrIdOrderByIdAsc(Long crId);

    List<TestCase> findByCycleIdOrderByIdAsc(Long cycleId);

    long countByCycleIdAndStatusIn(Long cycleId, List<com.duc.crqa.common.enums.TestCaseStatus> statuses);

    long countByCrId(Long crId);

    long countByTestCaseNumberStartingWith(String prefix);

    @Query("select t.status, count(t) from TestCase t where t.cr.id = :crId group by t.status")
    List<Object[]> countGroupByStatusForCr(@Param("crId") Long crId);

    @Query("select t.cr.id, t.status, count(t) from TestCase t group by t.cr.id, t.status")
    List<Object[]> countGroupByCrAndStatus();

    @Query("select t.executedBy, t.status, count(t) from TestCase t "
            + "where t.executedBy is not null and t.executedAt >= :from and t.executedAt <= :to "
            + "group by t.executedBy, t.status")
    List<Object[]> countByExecutorAndStatusBetween(@Param("from") LocalDateTime from,
                                                   @Param("to") LocalDateTime to);

    // The test case's epic link mirrors the change request's CR number; keep it in sync when the number changes.
    @Modifying
    @Query("update TestCase t set t.epicLink = :crNumber where t.cr.id = :crId")
    void updateEpicLinkByCrId(@Param("crId") Long crId, @Param("crNumber") String crNumber);
}

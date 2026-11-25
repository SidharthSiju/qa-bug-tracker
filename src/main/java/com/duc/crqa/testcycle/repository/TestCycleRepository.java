package com.duc.crqa.testcycle.repository;

import com.duc.crqa.testcycle.entity.TestCycle;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TestCycleRepository extends JpaRepository<TestCycle, Long>, JpaSpecificationExecutor<TestCycle> {

    List<TestCycle> findByCrIdOrderByCycleNumberDesc(Long crId);

    Optional<TestCycle> findTopByCrIdOrderByCycleNumberDesc(Long crId);

    Optional<TestCycle> findByCrIdAndCycleNumber(Long crId, Integer cycleNumber);
}

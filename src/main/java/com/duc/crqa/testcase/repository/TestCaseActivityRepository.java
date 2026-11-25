package com.duc.crqa.testcase.repository;

import com.duc.crqa.testcase.entity.TestCaseActivity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TestCaseActivityRepository extends JpaRepository<TestCaseActivity, Long>,
        JpaSpecificationExecutor<TestCaseActivity> {

    List<TestCaseActivity> findByTestCaseIdOrderByPerformedAtDesc(Long testCaseId);
}

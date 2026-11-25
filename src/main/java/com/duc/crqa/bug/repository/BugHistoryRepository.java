package com.duc.crqa.bug.repository;

import com.duc.crqa.bug.entity.BugHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BugHistoryRepository extends JpaRepository<BugHistory, Long>,
        JpaSpecificationExecutor<BugHistory> {

    List<BugHistory> findByBugIdOrderByPerformedAtDesc(Long bugId);
}

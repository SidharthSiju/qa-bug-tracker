package com.duc.crqa.bug.repository;

import com.duc.crqa.bug.entity.BugComment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BugCommentRepository extends JpaRepository<BugComment, Long> {

    List<BugComment> findByBugIdOrderByCreatedAtAsc(Long bugId);
}

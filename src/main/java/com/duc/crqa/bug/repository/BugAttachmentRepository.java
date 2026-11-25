package com.duc.crqa.bug.repository;

import com.duc.crqa.bug.entity.BugAttachment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BugAttachmentRepository extends JpaRepository<BugAttachment, Long> {

    List<BugAttachment> findByBugIdOrderByUploadedAtDesc(Long bugId);
}

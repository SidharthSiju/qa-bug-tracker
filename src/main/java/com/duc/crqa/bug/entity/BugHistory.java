package com.duc.crqa.bug.entity;

import com.duc.crqa.common.entity.IdEntity;
import com.duc.crqa.common.enums.BugStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "IT_bug_history")
@Getter
@Setter
@NoArgsConstructor
public class BugHistory extends IdEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "IT_bug_history_seq_gen")
    @SequenceGenerator(name = "IT_bug_history_seq_gen", sequenceName = "IT_BUG_HISTORY_SEQ", allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bug_id", nullable = false)
    private Bug bug;

    @Column(nullable = false, length = 100)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", length = 50)
    private BugStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", length = 50)
    private BugStatus newStatus;

    @Column(name = "old_assignee")
    private Long oldAssignee;

    @Column(name = "new_assignee")
    private Long newAssignee;

    @Column(name = "comment_text", length = 4000)
    private String comment;

    @Column(name = "performed_by", nullable = false)
    private Long performedBy;

    @Column(name = "performed_at", nullable = false)
    private LocalDateTime performedAt;

    @PrePersist
    void onCreate() {
        this.performedAt = LocalDateTime.now();
    }
}

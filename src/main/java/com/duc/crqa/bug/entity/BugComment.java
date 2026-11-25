package com.duc.crqa.bug.entity;

import com.duc.crqa.common.entity.AuditedEntity;
import com.duc.crqa.common.enums.CommentType;
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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "IT_bug_comment")
@Getter
@Setter
@NoArgsConstructor
public class BugComment extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "IT_bug_comment_seq_gen")
    @SequenceGenerator(name = "IT_bug_comment_seq_gen", sequenceName = "IT_BUG_COMMENT_SEQ", allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bug_id", nullable = false)
    private Bug bug;

    @Column(name = "comment_text", nullable = false, length = 4000)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(name = "comment_type", length = 50)
    private CommentType commentType = CommentType.COMMENT;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;
}

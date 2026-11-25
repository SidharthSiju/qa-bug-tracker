package com.duc.crqa.bug.entity;

import com.duc.crqa.common.entity.IdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
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
@Table(name = "IT_bug_attachment")
@Getter
@Setter
@NoArgsConstructor
public class BugAttachment extends IdEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "IT_bug_attachment_seq_gen")
    @SequenceGenerator(name = "IT_bug_attachment_seq_gen", sequenceName = "IT_BUG_ATTACHMENT_SEQ", allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bug_id", nullable = false)
    private Bug bug;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "storage_type", length = 30)
    private String storageType = "LOCAL_FILESYSTEM";

    @Column(name = "storage_path", length = 1000)
    private String storagePath;

    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @PrePersist
    void onCreate() {
        this.uploadedAt = LocalDateTime.now();
    }
}

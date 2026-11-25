package com.duc.crqa.cr.entity;

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
@Table(name = "IT_cr_activity")
@Getter
@Setter
@NoArgsConstructor
public class CrActivity extends IdEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "IT_cr_activity_seq_gen")
    @SequenceGenerator(name = "IT_cr_activity_seq_gen", sequenceName = "IT_CR_ACTIVITY_SEQ", allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cr_id", nullable = false)
    private ChangeRequest cr;

    @Column(name = "activity_type", length = 50)
    private String activityType;

    @Column(length = 4000)
    private String description;

    @Column(name = "performed_by")
    private Long performedBy;

    @Column(name = "performed_at", nullable = false)
    private LocalDateTime performedAt;

    @PrePersist
    void onCreate() {
        this.performedAt = LocalDateTime.now();
    }
}

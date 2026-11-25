package com.duc.crqa.testcycle.entity;

import com.duc.crqa.common.entity.IdEntity;
import com.duc.crqa.common.enums.TestCycleStatus;
import com.duc.crqa.cr.entity.ChangeRequest;
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
@Table(name = "IT_test_cycle")
@Getter
@Setter
@NoArgsConstructor
public class TestCycle extends IdEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "IT_test_cycle_seq_gen")
    @SequenceGenerator(name = "IT_test_cycle_seq_gen", sequenceName = "IT_TEST_CYCLE_SEQ", allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cr_id", nullable = false)
    private ChangeRequest cr;

    @Column(name = "cycle_number", nullable = false)
    private Integer cycleNumber;

    @Column(length = 200)
    private String name;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private TestCycleStatus status = TestCycleStatus.NOT_STARTED;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public String displayLabel() {
        return name == null || name.isBlank() ? "Cycle " + cycleNumber : name;
    }
}

package com.duc.crqa.bug.entity;

import com.duc.crqa.common.entity.AuditedEntity;
import com.duc.crqa.common.enums.BugStatus;
import com.duc.crqa.common.enums.DefectCategory;
import com.duc.crqa.common.enums.Priority;
import com.duc.crqa.common.enums.Resolution;
import com.duc.crqa.common.enums.Severity;
import com.duc.crqa.common.enums.TestPhase;
import com.duc.crqa.cr.entity.ChangeRequest;
import com.duc.crqa.testcase.entity.TestCase;
import com.duc.crqa.user.entity.User;
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
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "IT_bug")
@Getter
@Setter
@NoArgsConstructor
public class Bug extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "IT_bug_seq_gen")
    @SequenceGenerator(name = "IT_bug_seq_gen", sequenceName = "IT_BUG_SEQ", allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @Column(name = "bug_number", nullable = false, unique = true, length = 50)
    private String bugNumber;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cr_id", nullable = false)
    private ChangeRequest cr;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "test_case_id")
    private TestCase testCase;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, length = 4000)
    private String description;

    @Column(name = "steps_to_reproduce", length = 4000)
    private String stepsToReproduce;

    @Column(name = "expected_result", length = 4000)
    private String expectedResult;

    @Column(name = "actual_result", length = 4000)
    private String actualResult;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private BugStatus status = BugStatus.OPEN;

    @Column(length = 50)
    private String environment;

    @Column(length = 100)
    private String application;

    @Column(length = 100)
    private String module;

    @Column(length = 50)
    private String sprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "defect_category", length = 60)
    private DefectCategory defectCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "test_phase", length = 20)
    private TestPhase testPhaseFound;

    @Column(name = "external_defect_id", length = 100)
    private String externalDefectId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "reported_by", nullable = false)
    private User reportedBy;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    @Column(name = "reported_at", nullable = false)
    private LocalDateTime reportedAt;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "fixed_at")
    private LocalDateTime fixedAt;

    @Column(name = "retested_at")
    private LocalDateTime retestedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /** When the SLA escalation last notified this bug's owners (null when it never has). */
    @Column(name = "sla_escalated_at")
    private LocalDateTime slaEscalatedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private Resolution resolution;

    @Column(name = "resolution_comment", length = 4000)
    private String resolutionComment;

    @Version
    @Column(nullable = false)
    private Long version;
}

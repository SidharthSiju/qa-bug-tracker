package com.duc.crqa.cr.entity;

import com.duc.crqa.common.entity.AuditedEntity;
import com.duc.crqa.common.enums.Complexity;
import com.duc.crqa.common.enums.CrPriority;
import com.duc.crqa.common.enums.CrStatus;
import com.duc.crqa.common.enums.TestCaseReviewStatus;
import com.duc.crqa.project.entity.Project;
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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "IT_change_request",
        uniqueConstraints = @UniqueConstraint(name = "uq_change_request_cr_number_name",
                columnNames = {"cr_number", "cr_name"}))
@Getter
@Setter
@NoArgsConstructor
public class ChangeRequest extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "IT_change_request_seq_gen")
    @SequenceGenerator(name = "IT_change_request_seq_gen", sequenceName = "IT_CHANGE_REQUEST_SEQ", allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @Column(name = "cr_number", nullable = false, length = 50)
    private String crNumber;

    @Column(name = "cr_name", nullable = false, length = 255)
    private String crName;

    @Column(length = 4000)
    private String description;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(length = 50)
    private String environment;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private CrPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Complexity complexity;

    @Column(name = "sprint_name", length = 100)
    private String sprintName;

    @Column(name = "story_number", length = 100)
    private String storyNumber;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "development_owner_id")
    private User developmentOwner;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "qa_lead_id")
    private User qaLead;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sa_id")
    private User saLead;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CrStatus status = CrStatus.CREATED;

    @Column(name = "planned_start_date")
    private LocalDateTime plannedStartDate;

    @Column(name = "planned_end_date")
    private LocalDateTime plannedEndDate;

    @Column(name = "actual_start_date")
    private LocalDateTime actualStartDate;

    @Column(name = "actual_end_date")
    private LocalDateTime actualEndDate;

    @Column(name = "dac_submit_date")
    private LocalDateTime dacSubmitDate;

    @Column(name = "hld_submit_date")
    private LocalDateTime hldSubmitDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "tc_review_status", length = 30)
    private TestCaseReviewStatus tcReviewStatus = TestCaseReviewStatus.NOT_SUBMITTED;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Version
    @Column(nullable = false)
    private Long version;
}

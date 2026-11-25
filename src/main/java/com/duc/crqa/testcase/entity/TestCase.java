package com.duc.crqa.testcase.entity;

import com.duc.crqa.common.entity.IdEntity;
import com.duc.crqa.common.enums.Priority;
import com.duc.crqa.common.enums.TestCaseStatus;
import com.duc.crqa.cr.entity.ChangeRequest;
import com.duc.crqa.testcycle.entity.TestCycle;
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
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "IT_test_case")
@Getter
@Setter
@NoArgsConstructor
public class TestCase extends IdEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "IT_test_case_seq_gen")
    @SequenceGenerator(name = "IT_test_case_seq_gen", sequenceName = "IT_TEST_CASE_SEQ", allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @Column(name = "test_case_number", nullable = false, unique = true, length = 50)
    private String testCaseNumber;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cr_id", nullable = false)
    private ChangeRequest cr;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cycle_id")
    private TestCycle cycle;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 4000)
    private String description;

    @Column(length = 4000)
    private String precondition;

    @Column(name = "test_steps", length = 4000)
    private String testSteps;

    @Column(name = "expected_result", length = 4000)
    private String expectedResult;

    @Column(name = "actual_result", length = 4000)
    private String actualResult;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Priority priority;

    @Column(length = 500)
    private String labels;

    @Column(name = "issue_type", length = 50)
    private String issueType;

    @Column(name = "story_link", length = 100)
    private String storyLink;

    @Column(name = "epic_link", length = 100)
    private String epicLink;

    @Column(name = "issue_link", length = 100)
    private String issueLink;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private TestCaseStatus status = TestCaseStatus.NOT_EXECUTED;

    @Column(name = "executed_by")
    private Long executedBy;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;
}

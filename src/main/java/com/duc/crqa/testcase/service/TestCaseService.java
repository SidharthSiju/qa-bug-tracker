package com.duc.crqa.testcase.service;

import com.duc.crqa.audit.service.AuditService;
import com.duc.crqa.common.archive.service.ArchiveService;
import com.duc.crqa.common.enums.TestCaseStatus;
import com.duc.crqa.common.exception.ResourceNotFoundException;
import com.duc.crqa.common.util.NumberGeneratorService;
import com.duc.crqa.cr.entity.ChangeRequest;
import com.duc.crqa.cr.repository.ChangeRequestRepository;
import com.duc.crqa.security.CurrentUserService;
import com.duc.crqa.testcase.dto.TestCaseForm;
import com.duc.crqa.testcase.dto.TestCaseSearchCriteria;
import com.duc.crqa.testcase.entity.TestCase;
import com.duc.crqa.testcase.entity.TestCaseActivity;
import com.duc.crqa.testcase.repository.TestCaseActivityRepository;
import com.duc.crqa.testcase.repository.TestCaseRepository;
import com.duc.crqa.testcycle.entity.TestCycle;
import com.duc.crqa.testcycle.repository.TestCycleRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TestCaseService {

    private final TestCaseRepository testCaseRepository;
    private final TestCycleRepository testCycleRepository;
    private final ChangeRequestRepository changeRequestRepository;
    private final NumberGeneratorService numberGeneratorService;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;
    private final ArchiveService archiveService;
    private final TestCaseActivityRepository testCaseActivityRepository;

    public TestCaseService(TestCaseRepository testCaseRepository,
                           TestCycleRepository testCycleRepository,
                           ChangeRequestRepository changeRequestRepository,
                           NumberGeneratorService numberGeneratorService,
                           CurrentUserService currentUserService,
                           AuditService auditService,
                           ArchiveService archiveService,
                           TestCaseActivityRepository testCaseActivityRepository) {
        this.testCaseRepository = testCaseRepository;
        this.testCycleRepository = testCycleRepository;
        this.changeRequestRepository = changeRequestRepository;
        this.numberGeneratorService = numberGeneratorService;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
        this.archiveService = archiveService;
        this.testCaseActivityRepository = testCaseActivityRepository;
    }

    @Transactional
    public TestCase create(TestCaseForm form) {
        ChangeRequest cr = changeRequestRepository.findById(form.getCrId())
                .orElseThrow(() -> new ResourceNotFoundException("Change request not found: " + form.getCrId()));

        TestCase testCase = new TestCase();
        testCase.setTestCaseNumber(numberGeneratorService.nextTestCaseNumber());
        testCase.setCr(cr);
        testCase.setStatus(TestCaseStatus.NOT_EXECUTED);
        applyForm(testCase, form);

        TestCase saved = testCaseRepository.save(testCase);
        addActivity(saved, "CREATED", null, saved.getStatus(), null);
        auditService.record("TEST_CASE_CREATED", "TEST_CASE", saved.getId(), null, saved.getTestCaseNumber());
        return saved;
    }

    @Transactional
    public TestCase update(Long id, TestCaseForm form) {
        TestCase testCase = getById(id);
        applyForm(testCase, form);
        TestCase saved = testCaseRepository.save(testCase);
        auditService.record("TEST_CASE_UPDATED", "TEST_CASE", saved.getId(), null, saved.getTestCaseNumber());
        return saved;
    }

    private void applyForm(TestCase testCase, TestCaseForm form) {
        testCase.setTitle(form.getTitle());
        testCase.setDescription(form.getDescription());
        testCase.setPrecondition(form.getPrecondition());
        testCase.setTestSteps(form.getTestSteps());
        testCase.setExpectedResult(form.getExpectedResult());
        testCase.setPriority(form.getPriority());
        testCase.setLabels(form.getLabels());
        testCase.setIssueType(form.getIssueType());
        testCase.setIssueLink(form.getIssueLink());

        ChangeRequest cr = testCase.getCr();
        testCase.setStoryLink(cr == null ? null : cr.getStoryNumber());
        testCase.setEpicLink(cr == null ? null : cr.getCrNumber());

        if (form.getCycleId() != null) {
            TestCycle cycle = testCycleRepository.findById(form.getCycleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Test cycle not found: " + form.getCycleId()));
            testCase.setCycle(cycle);
        } else {
            testCase.setCycle(null);
        }
    }

    @Transactional(readOnly = true)
    public TestCase getById(Long id) {
        return testCaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Test case not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<TestCase> listAll() {
        return testCaseRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Page<TestCase> search(TestCaseSearchCriteria criteria, Pageable pageable) {
        return testCaseRepository.findAll(buildSpecification(criteria), pageable);
    }

    private Specification<TestCase> buildSpecification(TestCaseSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.getSearch() != null && !criteria.getSearch().isBlank()) {
                String like = "%" + criteria.getSearch().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("testCaseNumber")), like),
                        cb.like(cb.lower(root.get("title")), like)));
            }
            if (criteria.getCrId() != null) {
                predicates.add(cb.equal(root.get("cr").get("id"), criteria.getCrId()));
            }
            if (criteria.getCycleId() != null) {
                predicates.add(cb.equal(root.get("cycle").get("id"), criteria.getCycleId()));
            }
            if (criteria.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.getStatus()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Transactional(readOnly = true)
    public List<TestCase> listByCycle(Long cycleId) {
        return testCaseRepository.findByCycleIdOrderByIdAsc(cycleId);
    }

    @Transactional(readOnly = true)
    public List<TestCase> listByCr(Long crId) {
        return testCaseRepository.findByCrIdOrderByIdAsc(crId);
    }

    @Transactional(readOnly = true)
    public long countByCr(Long crId) {
        return testCaseRepository.countByCrId(crId);
    }

    @Transactional
    public void updateEpicLinkForCr(Long crId, String crNumber) {
        testCaseRepository.updateEpicLinkByCrId(crId, crNumber);
    }

    @Transactional
    public int deleteAll(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        List<TestCase> cases = testCaseRepository.findAllById(ids);
        // Copy into the archive, unlink referencing bugs and remove from the live table.
        archiveService.archiveAndDeleteTestCases(ids);
        for (TestCase testCase : cases) {
            auditService.record("TEST_CASE_DELETED", "TEST_CASE", testCase.getId(),
                    testCase.getTestCaseNumber(), null);
        }
        return cases.size();
    }

    @Transactional
    public TestCase execute(Long id, TestCaseStatus status, String actualResult) {
        return applyExecution(getById(id), status, actualResult);
    }

    /**
     * Records the same result on every selected test case, exactly as if each had been executed on its own — one
     * activity row and one audit row per case. A blank actual result leaves each case's existing actual result alone,
     * so a bulk status update never wipes detail that was recorded earlier.
     *
     * @return how many test cases were executed
     */
    @Transactional
    public int executeAll(List<Long> ids, TestCaseStatus status, String actualResult) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        List<TestCase> testCases = testCaseRepository.findAllById(ids);
        boolean hasResult = actualResult != null && !actualResult.isBlank();
        for (TestCase testCase : testCases) {
            applyExecution(testCase, status, hasResult ? actualResult : testCase.getActualResult());
        }
        return testCases.size();
    }

    private TestCase applyExecution(TestCase testCase, TestCaseStatus status, String actualResult) {
        TestCaseStatus old = testCase.getStatus();
        testCase.setStatus(status);
        testCase.setActualResult(actualResult);
        testCase.setExecutedBy(currentUserService.currentUserId());
        testCase.setExecutedAt(LocalDateTime.now());
        TestCase saved = testCaseRepository.save(testCase);
        addActivity(saved, "EXECUTED", old, status, actualResult);
        auditService.record("TEST_CASE_EXECUTED", "TEST_CASE", saved.getId(),
                old == null ? null : old.name(), status.name());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<TestCaseActivity> history(Long testCaseId) {
        return testCaseActivityRepository.findByTestCaseIdOrderByPerformedAtDesc(testCaseId);
    }

    @Transactional(readOnly = true)
    public Page<TestCaseActivity> searchHistory(Long testCaseId, String search, Pageable pageable) {
        return testCaseActivityRepository.findAll(buildHistorySpecification(testCaseId, search), pageable);
    }

    private Specification<TestCaseActivity> buildHistorySpecification(Long testCaseId, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("testCase").get("id"), testCaseId));
            if (search != null && !search.isBlank()) {
                String like = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("action")), like),
                        cb.like(cb.lower(root.get("comment")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void addActivity(TestCase testCase, String action, TestCaseStatus oldStatus, TestCaseStatus newStatus,
                             String comment) {
        TestCaseActivity activity = new TestCaseActivity();
        activity.setTestCase(testCase);
        activity.setAction(action);
        activity.setOldStatus(oldStatus);
        activity.setNewStatus(newStatus);
        activity.setComment(comment);
        activity.setPerformedBy(currentUserService.currentUserId());
        testCaseActivityRepository.save(activity);
    }
}

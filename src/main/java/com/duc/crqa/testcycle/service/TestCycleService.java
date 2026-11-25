package com.duc.crqa.testcycle.service;

import com.duc.crqa.audit.service.AuditService;
import com.duc.crqa.common.archive.service.ArchiveService;
import com.duc.crqa.common.enums.TestCycleStatus;
import com.duc.crqa.common.exception.ResourceNotFoundException;
import com.duc.crqa.common.exception.WorkflowException;
import com.duc.crqa.cr.entity.ChangeRequest;
import com.duc.crqa.cr.repository.ChangeRequestRepository;
import com.duc.crqa.security.CurrentUserService;
import com.duc.crqa.testcycle.dto.TestCycleForm;
import com.duc.crqa.testcycle.dto.TestCycleSearchCriteria;
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
public class TestCycleService {

    private final TestCycleRepository testCycleRepository;
    private final ChangeRequestRepository changeRequestRepository;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;
    private final ArchiveService archiveService;

    public TestCycleService(TestCycleRepository testCycleRepository,
                            ChangeRequestRepository changeRequestRepository,
                            CurrentUserService currentUserService,
                            AuditService auditService,
                            ArchiveService archiveService) {
        this.testCycleRepository = testCycleRepository;
        this.changeRequestRepository = changeRequestRepository;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
        this.archiveService = archiveService;
    }

    @Transactional
    public int deleteAll(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        List<TestCycle> cycles = testCycleRepository.findAllById(ids);
        // Copy into the archive, unlink referencing test cases and remove from the live table.
        archiveService.archiveAndDeleteTestCycles(ids);
        for (TestCycle cycle : cycles) {
            auditService.record("TEST_CYCLE_DELETED", "TEST_CYCLE", cycle.getId(), cycle.displayLabel(), null);
        }
        return cycles.size();
    }

    @Transactional
    public TestCycle create(TestCycleForm form) {
        ChangeRequest cr = changeRequestRepository.findById(form.getCrId())
                .orElseThrow(() -> new ResourceNotFoundException("Change request not found: " + form.getCrId()));

        int nextNumber = testCycleRepository.findTopByCrIdOrderByCycleNumberDesc(form.getCrId())
                .map(cycle -> cycle.getCycleNumber() + 1)
                .orElse(1);

        TestCycle cycle = new TestCycle();
        cycle.setCr(cr);
        cycle.setCycleNumber(nextNumber);
        cycle.setName(form.getName());
        cycle.setStartDate(form.getStartDate() == null ? null : form.getStartDate().atStartOfDay());
        cycle.setEndDate(form.getEndDate() == null ? null : form.getEndDate().atStartOfDay());
        cycle.setStatus(TestCycleStatus.NOT_STARTED);
        cycle.setCreatedBy(currentUserService.currentUserId());

        TestCycle saved = testCycleRepository.save(cycle);
        auditService.record("TEST_CYCLE_CREATED", "TEST_CYCLE", saved.getId(), null, saved.displayLabel());
        return saved;
    }

    @Transactional(readOnly = true)
    public TestCycle getById(Long id) {
        return testCycleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Test cycle not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<TestCycle> listAll() {
        return testCycleRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Page<TestCycle> search(TestCycleSearchCriteria criteria, Pageable pageable) {
        return testCycleRepository.findAll(buildSpecification(criteria), pageable);
    }

    private Specification<TestCycle> buildSpecification(TestCycleSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.getSearch() != null && !criteria.getSearch().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")),
                        "%" + criteria.getSearch().toLowerCase() + "%"));
            }
            if (criteria.getCrId() != null) {
                predicates.add(cb.equal(root.get("cr").get("id"), criteria.getCrId()));
            }
            if (criteria.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.getStatus()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Transactional(readOnly = true)
    public List<TestCycle> listByCr(Long crId) {
        return testCycleRepository.findByCrIdOrderByCycleNumberDesc(crId);
    }

    @Transactional
    public void start(Long id) {
        TestCycle cycle = getById(id);
        if (cycle.getStatus() != TestCycleStatus.NOT_STARTED) {
            throw new WorkflowException("Only a not-started test cycle can be started");
        }
        cycle.setStatus(TestCycleStatus.IN_PROGRESS);
        if (cycle.getStartDate() == null) {
            cycle.setStartDate(LocalDateTime.now());
        }
        testCycleRepository.save(cycle);
        auditService.record("TEST_CYCLE_STARTED", "TEST_CYCLE", id, TestCycleStatus.NOT_STARTED.name(),
                TestCycleStatus.IN_PROGRESS.name());
    }

    @Transactional
    public void complete(Long id) {
        TestCycle cycle = getById(id);
        if (cycle.getStatus() != TestCycleStatus.IN_PROGRESS) {
            throw new WorkflowException("Only an in-progress test cycle can be completed");
        }
        cycle.setStatus(TestCycleStatus.COMPLETED);
        if (cycle.getEndDate() == null) {
            cycle.setEndDate(LocalDateTime.now());
        }
        testCycleRepository.save(cycle);
        auditService.record("TEST_CYCLE_COMPLETED", "TEST_CYCLE", id, TestCycleStatus.IN_PROGRESS.name(),
                TestCycleStatus.COMPLETED.name());
    }
}

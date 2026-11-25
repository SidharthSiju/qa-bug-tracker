package com.duc.crqa.cr.service;

import com.duc.crqa.audit.service.AuditService;
import com.duc.crqa.common.archive.service.ArchiveService;
import com.duc.crqa.common.enums.CrStatus;
import com.duc.crqa.common.exception.ConcurrencyException;
import com.duc.crqa.common.exception.ResourceNotFoundException;
import com.duc.crqa.common.enums.Complexity;
import com.duc.crqa.common.enums.CrPriority;
import com.duc.crqa.common.util.NumberGeneratorService;
import com.duc.crqa.config.DefaultTestCaseProperties;
import com.duc.crqa.cr.dto.CrForm;
import com.duc.crqa.cr.dto.CrSearchCriteria;
import com.duc.crqa.cr.entity.ChangeRequest;
import com.duc.crqa.cr.entity.CrActivity;
import com.duc.crqa.cr.repository.ChangeRequestRepository;
import com.duc.crqa.cr.repository.CrActivityRepository;
import com.duc.crqa.jira.dto.JiraCrMapping;
import com.duc.crqa.notification.service.NotificationService;
import com.duc.crqa.project.entity.Project;
import com.duc.crqa.project.repository.ProjectRepository;
import com.duc.crqa.project.service.ProjectService;
import com.duc.crqa.security.CurrentUserService;
import com.duc.crqa.testcase.dto.TestCaseForm;
import com.duc.crqa.testcase.service.TestCaseService;
import com.duc.crqa.testcycle.dto.TestCycleForm;
import com.duc.crqa.testcycle.entity.TestCycle;
import com.duc.crqa.testcycle.service.TestCycleService;
import com.duc.crqa.user.entity.User;
import com.duc.crqa.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChangeRequestService {

    /** Environment applied to change requests imported from JIRA. */
    public static final String JIRA_DEFAULT_ENVIRONMENT = "SIT";

    private final ChangeRequestRepository changeRequestRepository;
    private final CrActivityRepository crActivityRepository;
    private final ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final UserRepository userRepository;
    private final NumberGeneratorService numberGeneratorService;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;
    private final TestCaseService testCaseService;
    private final TestCycleService testCycleService;
    private final DefaultTestCaseProperties defaultTestCaseProperties;
    private final ArchiveService archiveService;
    private final NotificationService notificationService;

    public ChangeRequestService(ChangeRequestRepository changeRequestRepository,
                                CrActivityRepository crActivityRepository,
                                ProjectRepository projectRepository,
                                ProjectService projectService,
                                UserRepository userRepository,
                                NumberGeneratorService numberGeneratorService,
                                CurrentUserService currentUserService,
                                AuditService auditService,
                                TestCaseService testCaseService,
                                TestCycleService testCycleService,
                                DefaultTestCaseProperties defaultTestCaseProperties,
                                ArchiveService archiveService,
                                NotificationService notificationService) {
        this.changeRequestRepository = changeRequestRepository;
        this.crActivityRepository = crActivityRepository;
        this.projectRepository = projectRepository;
        this.projectService = projectService;
        this.userRepository = userRepository;
        this.numberGeneratorService = numberGeneratorService;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
        this.testCaseService = testCaseService;
        this.testCycleService = testCycleService;
        this.defaultTestCaseProperties = defaultTestCaseProperties;
        this.archiveService = archiveService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public Page<ChangeRequest> search(CrSearchCriteria criteria, Pageable pageable) {
        return changeRequestRepository.findAll(buildSpecification(criteria), pageable);
    }

    @Transactional(readOnly = true)
    public List<ChangeRequest> findAll() {
        return changeRequestRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public ChangeRequest getById(Long id) {
        return changeRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Change request not found: " + id));
    }

    @Transactional
    public ChangeRequest create(CrForm form) {
        ChangeRequest cr = new ChangeRequest();
        cr.setCrNumber(resolveCrNumber(form));
        applyForm(cr, form);
        cr.setStatus(CrStatus.CREATED);
        cr.setCreatedBy(currentUserService.currentUserId());
        cr.setUpdatedBy(currentUserService.currentUserId());

        ChangeRequest saved = changeRequestRepository.save(cr);
        addActivity(saved, "CREATED", "Change request created");
        if (defaultTestCaseProperties.isEnabled()) {
            TestCycle defaultCycle = createDefaultTestCycle(saved);
            createDefaultTestCase(saved, defaultCycle.getId());
        }
        auditService.record("CR_CREATED", "CHANGE_REQUEST", saved.getId(), null, saved.getCrNumber());
        return saved;
    }

    private TestCycle createDefaultTestCycle(ChangeRequest cr) {
        TestCycleForm form = new TestCycleForm();
        form.setCrId(cr.getId());
        form.setName("Default test cycle");
        return testCycleService.create(form);
    }

    private void createDefaultTestCase(ChangeRequest cr, Long cycleId) {
        TestCaseForm form = new TestCaseForm();
        form.setCrId(cr.getId());
        form.setCycleId(cycleId);
        form.setTitle("Default test case");
        form.setDescription("Auto-generated default test case for " + cr.getCrNumber());
        testCaseService.create(form);
    }

    private String resolveCrNumber(CrForm form) {
        String requested = form.getCrNumber();
        if (requested == null || requested.isBlank()) {
            return numberGeneratorService.nextCrNumber();
        }
        String crNumber = requested.trim();
        if (changeRequestRepository.existsByCrNumberAndCrName(crNumber, form.getCrName())) {
            throw new IllegalArgumentException("A change request with this Epic number and name already exists");
        }
        return crNumber;
    }

    @Transactional
    public ChangeRequest update(Long id, CrForm form) {
        ChangeRequest cr = getById(id);
        if (!cr.getVersion().equals(form.getVersion())) {
            throw new ConcurrencyException(
                    "This change request was modified by another user. Refresh the page and review the latest information before saving.");
        }
        String crNumber = form.getCrNumber() == null ? null : form.getCrNumber().trim();
        if (crNumber == null || crNumber.isBlank()) {
            throw new IllegalArgumentException("Epic number is required");
        }
        if (changeRequestRepository.existsByCrNumberAndCrNameAndIdNot(crNumber, form.getCrName(), id)) {
            throw new IllegalArgumentException("A change request with this Epic number and name already exists");
        }
        boolean crNumberChanged = !crNumber.equals(cr.getCrNumber());
        String oldValue = summarize(cr);
        cr.setCrNumber(crNumber);
        applyForm(cr, form);
        cr.setUpdatedBy(currentUserService.currentUserId());
        ChangeRequest saved = changeRequestRepository.save(cr);
        if (crNumberChanged) {
            // Test cases mirror the CR number in their epic link; keep them consistent with the new number.
            testCaseService.updateEpicLinkForCr(id, crNumber);
        }
        auditService.record("CR_UPDATED", "CHANGE_REQUEST", saved.getId(), oldValue, summarize(saved));
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        ChangeRequest cr = getById(id);
        // Move the CR and its whole subtree (cycles, test cases, bugs, activities) into the archive tables.
        archiveService.archiveAndDeleteChangeRequest(id);
        auditService.record("CHANGE_REQUEST_DELETED", "CHANGE_REQUEST", id, cr.getCrNumber(), null);
    }

    @Transactional
    public int deleteAll(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        List<ChangeRequest> changeRequests = changeRequestRepository.findAllById(ids);
        for (ChangeRequest cr : changeRequests) {
            archiveService.archiveAndDeleteChangeRequest(cr.getId());
            auditService.record("CHANGE_REQUEST_DELETED", "CHANGE_REQUEST", cr.getId(), cr.getCrNumber(), null);
        }
        return changeRequests.size();
    }

    @Transactional(readOnly = true)
    public Optional<ChangeRequest> findByCrNumberAndCrName(String crNumber, String crName) {
        return changeRequestRepository.findFirstByCrNumberAndCrName(crNumber, crName);
    }

    /**
     * Inserts or refreshes the change request described by a JIRA issue.
     *
     * <p>An existing change request is matched on (Epic number, name) and refreshed with the JIRA-owned values — story
     * number, description, DAC and HLD submit dates. Its project and environment are only filled in when they are still
     * empty, so values maintained in this application survive a re-import. The change request's QA Lead and SA Lead are
     * notified that an existing change request was updated from JIRA.
     *
     * @return {@code true} when a new change request was inserted, {@code false} when an existing one was refreshed
     */
    @Transactional
    public boolean importFromJira(JiraCrMapping mapping) {
        String crNumber = trimToNull(mapping.crNumber());
        if (crNumber == null) {
            throw new IllegalArgumentException("the JIRA issue has no epic number (parent key)");
        }
        String crName = trimToNull(mapping.crName());
        if (crName == null) {
            throw new IllegalArgumentException("the JIRA issue has no summary to use as the change request name");
        }
        String storyNumber = trimToNull(mapping.storyNumber());
        String description = trimToNull(mapping.description());

        Optional<ChangeRequest> existing = changeRequestRepository.findFirstByCrNumberAndCrName(crNumber, crName);
        if (existing.isPresent()) {
            ChangeRequest cr = existing.get();
            String oldValue = summarize(cr);

            cr.setStoryNumber(storyNumber);
            if (description != null) {
                cr.setDescription(description);
            }
            if (mapping.dacSubmitDate() != null) {
                cr.setDacSubmitDate(mapping.dacSubmitDate().atStartOfDay());
            }
            if (mapping.hldSubmitDate() != null) {
                cr.setHldSubmitDate(mapping.hldSubmitDate().atStartOfDay());
            }
            if (cr.getProject() == null) {
                cr.setProject(projectService.getOrCreateDefaultProject());
            }
            if (cr.getEnvironment() == null || cr.getEnvironment().isBlank()) {
                cr.setEnvironment(JIRA_DEFAULT_ENVIRONMENT);
            }
            cr.setUpdatedBy(currentUserService.currentUserId());

            ChangeRequest saved = changeRequestRepository.save(cr);
            addActivity(saved, "JIRA_IMPORT", "Updated from JIRA import (story " + storyNumber + ")");
            auditService.record("CR_UPDATED_FROM_JIRA", "CHANGE_REQUEST", saved.getId(), oldValue, summarize(saved));
            notificationService.notifyCrUpdatedFromJira(saved);
            return false;
        }

        CrForm form = new CrForm();
        form.setCrNumber(crNumber);
        form.setCrName(crName);
        form.setStoryNumber(storyNumber);
        form.setDescription(description);
        form.setEnvironment(JIRA_DEFAULT_ENVIRONMENT);
        form.setPriority(CrPriority.MEDIUM);
        form.setComplexity(Complexity.MEDIUM);
        form.setDacSubmitDate(mapping.dacSubmitDate());
        form.setHldSubmitDate(mapping.hldSubmitDate());
        // The project is left unset so the default project is used; every other field keeps its default value and can
        // be completed later from the change request screens.

        ChangeRequest saved = create(form);
        auditService.record("CR_IMPORTED_FROM_JIRA", "CHANGE_REQUEST", saved.getId(), null, saved.getCrNumber());
        return true;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Transactional
    public void addActivity(ChangeRequest cr, String activityType, String description) {
        CrActivity activity = new CrActivity();
        activity.setCr(cr);
        activity.setActivityType(activityType);
        activity.setDescription(description);
        activity.setPerformedBy(currentUserService.currentUserId());
        crActivityRepository.save(activity);
    }

    @Transactional(readOnly = true)
    public List<CrActivity> activities(Long crId) {
        return crActivityRepository.findByCrIdOrderByPerformedAtDesc(crId);
    }

    @Transactional(readOnly = true)
    public Page<CrActivity> searchActivities(Long crId, String search, Pageable pageable) {
        return crActivityRepository.findAll(buildActivitySpecification(crId, search), pageable);
    }

    private Specification<CrActivity> buildActivitySpecification(Long crId, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("cr").get("id"), crId));
            if (search != null && !search.isBlank()) {
                String like = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("activityType")), like),
                        cb.like(cb.lower(root.get("description")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Transactional(readOnly = true)
    public long countByStatus(CrStatus status) {
        return changeRequestRepository.countByStatus(status);
    }

    @Transactional(readOnly = true)
    public long countByStatusIn(List<CrStatus> statuses) {
        return changeRequestRepository.countByStatusIn(statuses);
    }

    private void applyForm(ChangeRequest cr, CrForm form) {
        cr.setCrName(form.getCrName());
        cr.setDescription(form.getDescription());
        cr.setEnvironment(form.getEnvironment());
        cr.setPriority(form.getPriority());
        cr.setComplexity(form.getComplexity());
        cr.setSprintName(form.getSprintName());
        cr.setStoryNumber(form.getStoryNumber());
        cr.setPlannedStartDate(form.getPlannedStartDate() == null ? null : form.getPlannedStartDate().atStartOfDay());
        cr.setPlannedEndDate(form.getPlannedEndDate() == null ? null : form.getPlannedEndDate().atStartOfDay());
        cr.setActualStartDate(form.getActualStartDate() == null ? null : form.getActualStartDate().atStartOfDay());
        cr.setActualEndDate(form.getActualEndDate() == null ? null : form.getActualEndDate().atStartOfDay());
        cr.setDacSubmitDate(form.getDacSubmitDate() == null ? null : form.getDacSubmitDate().atStartOfDay());
        cr.setHldSubmitDate(form.getHldSubmitDate() == null ? null : form.getHldSubmitDate().atStartOfDay());

        if (form.getProjectId() != null) {
            cr.setProject(projectRepository.findById(form.getProjectId()).orElse(null));
        } else {
            cr.setProject(projectService.getOrCreateDefaultProject());
        }
        if (form.getDevelopmentOwnerId() != null) {
            cr.setDevelopmentOwner(resolveUser(form.getDevelopmentOwnerId()));
        } else {
            cr.setDevelopmentOwner(null);
        }
        if (form.getQaLeadId() != null) {
            cr.setQaLead(resolveUser(form.getQaLeadId()));
        } else {
            cr.setQaLead(null);
        }
        if (form.getSaLeadId() != null) {
            cr.setSaLead(resolveUser(form.getSaLeadId()));
        } else {
            cr.setSaLead(null);
        }
    }

    private User resolveUser(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    private Specification<ChangeRequest> buildSpecification(CrSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.getSearch() != null && !criteria.getSearch().isBlank()) {
                String like = "%" + criteria.getSearch().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("crNumber")), like),
                        cb.like(cb.lower(root.get("crName")), like)));
            }
            if (criteria.getProjectId() != null) {
                predicates.add(cb.equal(root.get("project").get("id"), criteria.getProjectId()));
            }
            if (criteria.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.getStatus()));
            }
            if (criteria.getPriority() != null) {
                predicates.add(cb.equal(root.get("priority"), criteria.getPriority()));
            }
            if (criteria.getComplexity() != null) {
                predicates.add(cb.equal(root.get("complexity"), criteria.getComplexity()));
            }
            if (criteria.getSprintName() != null && !criteria.getSprintName().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("sprintName")),
                        "%" + criteria.getSprintName().toLowerCase() + "%"));
            }
            if (criteria.getQaLeadId() != null) {
                predicates.add(cb.equal(root.get("qaLead").get("id"), criteria.getQaLeadId()));
            }
            if (criteria.getDevelopmentOwnerId() != null) {
                predicates.add(cb.equal(root.get("developmentOwner").get("id"), criteria.getDevelopmentOwnerId()));
            }
            if (criteria.getSaLeadId() != null) {
                predicates.add(cb.equal(root.get("saLead").get("id"), criteria.getSaLeadId()));
            }
            if (criteria.getFromDate() != null && !criteria.getFromDate().isBlank()) {
                LocalDate from = LocalDate.parse(criteria.getFromDate());
                predicates.add(cb.greaterThanOrEqualTo(root.get("plannedEndDate"), from.atStartOfDay()));
            }
            if (criteria.getToDate() != null && !criteria.getToDate().isBlank()) {
                LocalDate to = LocalDate.parse(criteria.getToDate());
                predicates.add(cb.lessThanOrEqualTo(root.get("plannedEndDate"), to.atTime(23, 59, 59)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String summarize(ChangeRequest cr) {
        return "CR=" + cr.getCrNumber() + ", name=" + cr.getCrName() + ", status=" + cr.getStatus();
    }
}

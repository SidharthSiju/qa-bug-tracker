package com.duc.crqa.bug.service;

import com.duc.crqa.attachment.service.AttachmentStorageService;
import com.duc.crqa.audit.service.AuditService;
import com.duc.crqa.bug.dto.BugForm;
import com.duc.crqa.bug.dto.BugSearchCriteria;
import com.duc.crqa.bug.dto.CommentForm;
import com.duc.crqa.bug.entity.Bug;
import com.duc.crqa.bug.entity.BugAttachment;
import com.duc.crqa.bug.entity.BugComment;
import com.duc.crqa.bug.entity.BugHistory;
import com.duc.crqa.bug.repository.BugAttachmentRepository;
import com.duc.crqa.bug.repository.BugCommentRepository;
import com.duc.crqa.bug.repository.BugHistoryRepository;
import com.duc.crqa.bug.repository.BugRepository;
import com.duc.crqa.common.archive.service.ArchiveService;
import com.duc.crqa.common.enums.BugStatus;
import com.duc.crqa.common.enums.Severity;
import com.duc.crqa.common.enums.SlaStatus;
import com.duc.crqa.common.exception.ConcurrencyException;
import com.duc.crqa.common.exception.ResourceNotFoundException;
import com.duc.crqa.common.util.NumberGeneratorService;
import com.duc.crqa.common.util.SlaService;
import com.duc.crqa.cr.entity.ChangeRequest;
import com.duc.crqa.cr.repository.ChangeRequestRepository;
import com.duc.crqa.notification.service.NotificationService;
import com.duc.crqa.security.CurrentUserService;
import com.duc.crqa.testcase.entity.TestCase;
import com.duc.crqa.testcase.repository.TestCaseRepository;
import com.duc.crqa.user.entity.User;
import com.duc.crqa.user.repository.UserRepository;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.CriteriaBuilder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class BugService {

    /** Bugs in these statuses are done: they never breach their SLA. */
    private static final List<BugStatus> CLOSED_STATUSES = List.of(BugStatus.CLOSED, BugStatus.REJECTED);

    private static final DateTimeFormatter DEADLINE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** One row's SLA state: the badge value and the deadline shown in its tooltip. */
    public record SlaView(SlaStatus status, String deadline) {
    }

    private final BugRepository bugRepository;
    private final BugCommentRepository bugCommentRepository;
    private final BugHistoryRepository bugHistoryRepository;
    private final BugAttachmentRepository bugAttachmentRepository;
    private final ChangeRequestRepository changeRequestRepository;
    private final TestCaseRepository testCaseRepository;
    private final UserRepository userRepository;
    private final NumberGeneratorService numberGeneratorService;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final AttachmentStorageService attachmentStorageService;
    private final ArchiveService archiveService;
    private final SlaService slaService;

    public BugService(BugRepository bugRepository,
                      BugCommentRepository bugCommentRepository,
                      BugHistoryRepository bugHistoryRepository,
                      BugAttachmentRepository bugAttachmentRepository,
                      ChangeRequestRepository changeRequestRepository,
                      TestCaseRepository testCaseRepository,
                      UserRepository userRepository,
                      NumberGeneratorService numberGeneratorService,
                      CurrentUserService currentUserService,
                      AuditService auditService,
                      NotificationService notificationService,
                      AttachmentStorageService attachmentStorageService,
                      ArchiveService archiveService,
                      SlaService slaService) {
        this.bugRepository = bugRepository;
        this.bugCommentRepository = bugCommentRepository;
        this.bugHistoryRepository = bugHistoryRepository;
        this.bugAttachmentRepository = bugAttachmentRepository;
        this.changeRequestRepository = changeRequestRepository;
        this.testCaseRepository = testCaseRepository;
        this.userRepository = userRepository;
        this.numberGeneratorService = numberGeneratorService;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.attachmentStorageService = attachmentStorageService;
        this.archiveService = archiveService;
        this.slaService = slaService;
    }

    /**
     * SLA state and deadline for each of the given bugs, computed in memory so a page of results costs no extra query.
     */
    @Transactional(readOnly = true)
    public Map<Long, SlaView> slaViews(List<Bug> bugs) {
        Map<Long, SlaView> views = new LinkedHashMap<>();
        for (Bug bug : bugs) {
            views.put(bug.getId(), new SlaView(slaService.status(bug),
                    slaService.deadline(bug).format(DEADLINE_FORMAT)));
        }
        return views;
    }

    /** How many bugs are currently in the given SLA state. */
    @Transactional(readOnly = true)
    public long countBySla(SlaStatus sla) {
        return bugRepository.count(specificationForSla(sla));
    }

    /** Every bug that is still open (used by the aging report). */
    @Transactional(readOnly = true)
    public List<Bug> listOpen() {
        return bugRepository.findByStatusNotIn(CLOSED_STATUSES);
    }

    /** Every bug currently in the given SLA state (used by the escalation sweep). */
    @Transactional(readOnly = true)
    public List<Bug> findBySla(SlaStatus sla) {
        return bugRepository.findAll(specificationForSla(sla));
    }

    private Specification<Bug> specificationForSla(SlaStatus sla) {
        BugSearchCriteria criteria = new BugSearchCriteria();
        criteria.setSla(sla);
        return buildSpecification(criteria);
    }

    @Transactional(readOnly = true)
    public Page<Bug> search(BugSearchCriteria criteria, Pageable pageable) {
        return bugRepository.findAll(buildSpecification(criteria), pageable);
    }

    @Transactional(readOnly = true)
    public Bug getById(Long id) {
        return bugRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bug not found: " + id));
    }

    @Transactional
    public Bug create(BugForm form, List<MultipartFile> attachments) {
        return create(form, attachments, true);
    }

    @Transactional
    public Bug create(BugForm form, List<MultipartFile> attachments, boolean notify) {
        ChangeRequest cr = changeRequestRepository.findById(form.getCrId())
                .orElseThrow(() -> new ResourceNotFoundException("Change request not found: " + form.getCrId()));

        Bug bug = new Bug();
        bug.setBugNumber(numberGeneratorService.nextBugNumber());
        bug.setCr(cr);
        bug.setReportedBy(resolveReporter(form.getReportedById()));
        bug.setReportedAt(LocalDateTime.now());

        BugStatus status = form.getStatus() == null ? BugStatus.OPEN : form.getStatus();
        bug.setStatus(status);
        if (form.getAssignedToId() != null) {
            User assignee = userRepository.findById(form.getAssignedToId()).orElse(null);
            bug.setAssignedTo(assignee);
            if (assignee != null) {
                bug.setAssignedAt(LocalDateTime.now());
            }
        }
        applyForm(bug, form);
        if (status == BugStatus.CLOSED) {
            bug.setClosedAt(LocalDateTime.now());
        }

        Bug saved = bugRepository.save(bug);
        boolean imported = status != BugStatus.OPEN;
        addHistory(saved, imported ? "IMPORTED" : "OPEN", null, status,
                null, form.getAssignedToId(), imported ? "Bug imported" : "Bug created");
        auditService.record("BUG_CREATED", "BUG", saved.getId(), null, saved.getBugNumber());

        if (attachments != null) {
            for (MultipartFile file : attachments) {
                if (!file.isEmpty()) {
                    attachmentStorageService.store(file, saved, currentUserService.currentUserId());
                }
            }
        }

        if (notify) {
            notificationService.notifyBugCreated(saved);
        }
        return saved;
    }

    private User resolveReporter(Long reportedById) {
        if (reportedById != null) {
            User reporter = userRepository.findById(reportedById).orElse(null);
            if (reporter != null) {
                return reporter;
            }
        }
        return currentUserService.currentUser();
    }

    @Transactional
    public Bug update(Long id, BugForm form) {
        Bug bug = getById(id);
        if (!bug.getVersion().equals(form.getVersion())) {
            throw new ConcurrencyException(
                    "This bug was modified by another user. Refresh the page and review the latest information before saving.");
        }
        String oldValue = summarize(bug);
        applyForm(bug, form);
        Bug saved = bugRepository.save(bug);
        auditService.record("BUG_UPDATED", "BUG", saved.getId(), oldValue, summarize(saved));
        return saved;
    }

    @Transactional
    public BugComment addComment(Long bugId, CommentForm form) {
        Bug bug = getById(bugId);
        BugComment comment = new BugComment();
        comment.setBug(bug);
        comment.setComment(form.getComment());
        comment.setCommentType(form.getCommentType());
        comment.setCreatedBy(currentUserService.currentUserId());
        BugComment saved = bugCommentRepository.save(comment);
        auditService.record("BUG_COMMENT", "BUG", bugId, null, form.getComment());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Bug> listByCr(Long crId) {
        return bugRepository.findByCrIdOrderByIdDesc(crId);
    }

    @Transactional
    public int deleteAll(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        List<Bug> bugs = bugRepository.findAllById(ids);
        // Copy the bug and its children into the archive, then remove them from the live tables.
        archiveService.archiveAndDeleteBugs(ids);
        for (Bug bug : bugs) {
            auditService.record("BUG_DELETED", "BUG", bug.getId(), bug.getBugNumber(), null);
        }
        return bugs.size();
    }

    @Transactional(readOnly = true)
    public List<BugComment> comments(Long bugId) {
        return bugCommentRepository.findByBugIdOrderByCreatedAtAsc(bugId);
    }

    @Transactional(readOnly = true)
    public List<BugHistory> history(Long bugId) {
        return bugHistoryRepository.findByBugIdOrderByPerformedAtDesc(bugId);
    }

    @Transactional(readOnly = true)
    public Page<BugHistory> searchHistory(Long bugId, String search, Pageable pageable) {
        return bugHistoryRepository.findAll(buildHistorySpecification(bugId, search), pageable);
    }

    private Specification<BugHistory> buildHistorySpecification(Long bugId, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("bug").get("id"), bugId));
            if (search != null && !search.isBlank()) {
                String like = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("action")), like),
                        cb.like(cb.lower(root.get("comment")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Transactional(readOnly = true)
    public List<BugAttachment> attachments(Long bugId) {
        return bugAttachmentRepository.findByBugIdOrderByUploadedAtDesc(bugId);
    }

    public void addHistory(Bug bug, String action, BugStatus oldStatus, BugStatus newStatus,
                           Long oldAssignee, Long newAssignee, String comment) {
        BugHistory history = new BugHistory();
        history.setBug(bug);
        history.setAction(action);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setOldAssignee(oldAssignee);
        history.setNewAssignee(newAssignee);
        history.setComment(comment);
        history.setPerformedBy(currentUserService.currentUserId());
        bugHistoryRepository.save(history);
    }

    private void applyForm(Bug bug, BugForm form) {
        bug.setTitle(form.getTitle());
        bug.setDescription(form.getDescription());
        bug.setStepsToReproduce(form.getStepsToReproduce());
        bug.setExpectedResult(form.getExpectedResult());
        bug.setActualResult(form.getActualResult());
        bug.setSeverity(form.getSeverity());
        bug.setPriority(form.getPriority());
        bug.setEnvironment(form.getEnvironment());
        bug.setApplication(form.getApplication());
        bug.setModule(form.getModule());
        bug.setSprint(form.getSprint());
        bug.setDefectCategory(form.getDefectCategory());
        bug.setTestPhaseFound(form.getTestPhaseFound());
        bug.setExternalDefectId(form.getExternalDefectId());

        if (form.getTestCaseId() != null) {
            bug.setTestCase(testCaseRepository.findById(form.getTestCaseId()).orElse(null));
        } else {
            bug.setTestCase(null);
        }
    }

    private Specification<Bug> buildSpecification(BugSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.getSearch() != null && !criteria.getSearch().isBlank()) {
                String like = "%" + criteria.getSearch().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("bugNumber")), like),
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("description")), like)));
            }
            if (criteria.getCrId() != null) {
                predicates.add(cb.equal(root.get("cr").get("id"), criteria.getCrId()));
            }
            if (criteria.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.getStatus()));
            }
            if (criteria.getSeverity() != null) {
                predicates.add(cb.equal(root.get("severity"), criteria.getSeverity()));
            }
            if (criteria.getPriority() != null) {
                predicates.add(cb.equal(root.get("priority"), criteria.getPriority()));
            }
            if (criteria.getAssignedToId() != null) {
                predicates.add(cb.equal(root.get("assignedTo").get("id"), criteria.getAssignedToId()));
            }
            if (criteria.getReportedById() != null) {
                predicates.add(cb.equal(root.get("reportedBy").get("id"), criteria.getReportedById()));
            }
            if (criteria.getApplication() != null && !criteria.getApplication().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("application")),
                        "%" + criteria.getApplication().toLowerCase() + "%"));
            }
            if (criteria.getModule() != null && !criteria.getModule().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("module")),
                        "%" + criteria.getModule().toLowerCase() + "%"));
            }
            if (criteria.getEnvironment() != null && !criteria.getEnvironment().isBlank()) {
                predicates.add(cb.equal(root.get("environment"), criteria.getEnvironment()));
            }
            if (criteria.getSprint() != null && !criteria.getSprint().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("sprint")),
                        "%" + criteria.getSprint().toLowerCase() + "%"));
            }
            if (criteria.getDefectCategory() != null) {
                predicates.add(cb.equal(root.get("defectCategory"), criteria.getDefectCategory()));
            }
            if (criteria.getTestPhaseFound() != null) {
                predicates.add(cb.equal(root.get("testPhaseFound"), criteria.getTestPhaseFound()));
            }
            if (criteria.getFromDate() != null && !criteria.getFromDate().isBlank()) {
                LocalDate from = LocalDate.parse(criteria.getFromDate());
                predicates.add(cb.greaterThanOrEqualTo(root.get("reportedAt"), from.atStartOfDay()));
            }
            if (criteria.getToDate() != null && !criteria.getToDate().isBlank()) {
                LocalDate to = LocalDate.parse(criteria.getToDate());
                predicates.add(cb.lessThanOrEqualTo(root.get("reportedAt"), to.atTime(23, 59, 59)));
            }
            if (criteria.getSla() != null) {
                predicates.add(slaPredicate(root, cb, criteria.getSla()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * The SLA state is derived from the severity and the reported time, so it is expressed here as one OR-branch per
     * severity. Doing it in SQL (rather than filtering a fetched page) keeps paging and sorting correct.
     */
    private Predicate slaPredicate(Root<Bug> root, CriteriaBuilder cb, SlaStatus sla) {
        LocalDateTime now = LocalDateTime.now();
        Expression<LocalDateTime> reportedAt = root.get("reportedAt");
        List<Predicate> branches = new ArrayList<>();

        for (Severity severity : Severity.values()) {
            long slaMinutes = Math.max(1, slaService.slaFor(severity).toMinutes());
            LocalDateTime breachedFrom = now.minusMinutes(slaMinutes);
            LocalDateTime approachingFrom = now.minusMinutes((long) Math.ceil(slaMinutes * 0.75));
            Predicate thisSeverity = cb.equal(root.get("severity"), severity);

            switch (sla) {
                case BREACHED -> branches.add(cb.and(thisSeverity,
                        cb.lessThanOrEqualTo(reportedAt, breachedFrom)));
                case APPROACHING -> branches.add(cb.and(thisSeverity,
                        cb.greaterThan(reportedAt, breachedFrom),
                        cb.lessThanOrEqualTo(reportedAt, approachingFrom)));
                case WITHIN -> branches.add(cb.and(thisSeverity,
                        cb.greaterThan(reportedAt, approachingFrom)));
            }
        }

        Predicate anySeverity = cb.or(branches.toArray(new Predicate[0]));
        if (sla == SlaStatus.WITHIN) {
            // Completed bugs count as within SLA, so they are not excluded from this filter.
            return anySeverity;
        }
        return cb.and(cb.not(root.get("status").in(CLOSED_STATUSES)), anySeverity);
    }

    private String summarize(Bug bug) {
        return "Bug=" + bug.getBugNumber() + ", title=" + bug.getTitle()
                + ", severity=" + bug.getSeverity() + ", status=" + bug.getStatus();
    }
}

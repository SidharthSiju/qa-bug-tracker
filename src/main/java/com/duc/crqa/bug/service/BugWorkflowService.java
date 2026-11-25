package com.duc.crqa.bug.service;

import com.duc.crqa.audit.service.AuditService;
import com.duc.crqa.bug.dto.CommentForm;
import com.duc.crqa.bug.dto.RetestForm;
import com.duc.crqa.bug.entity.Bug;
import com.duc.crqa.common.enums.BugStatus;
import com.duc.crqa.common.enums.CommentType;
import com.duc.crqa.common.enums.Resolution;
import com.duc.crqa.common.enums.Role;
import com.duc.crqa.common.exception.ResourceNotFoundException;
import com.duc.crqa.common.exception.WorkflowException;
import com.duc.crqa.notification.service.NotificationService;
import com.duc.crqa.security.CurrentUserService;
import com.duc.crqa.user.entity.User;
import com.duc.crqa.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BugWorkflowService {

    private static final Map<BugStatus, Set<BugStatus>> ALLOWED = Map.of(
            BugStatus.OPEN, Set.of(BugStatus.ASSIGNED, BugStatus.REJECTED),
            BugStatus.ASSIGNED, Set.of(BugStatus.IN_ANALYSIS, BugStatus.IN_DEVELOPMENT, BugStatus.FIXED,
                    BugStatus.REJECTED),
            BugStatus.IN_ANALYSIS, Set.of(BugStatus.IN_DEVELOPMENT, BugStatus.FIXED, BugStatus.REJECTED),
            BugStatus.IN_DEVELOPMENT, Set.of(BugStatus.FIXED),
            BugStatus.FIXED, Set.of(BugStatus.READY_FOR_RETEST),
            BugStatus.READY_FOR_RETEST, Set.of(BugStatus.RETESTING),
            BugStatus.RETESTING, Set.of(BugStatus.CLOSED, BugStatus.REOPENED),
            BugStatus.REOPENED, Set.of(BugStatus.IN_DEVELOPMENT),
            BugStatus.REJECTED, Set.of(),
            BugStatus.CLOSED, Set.of()
    );

    private final BugService bugService;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public BugWorkflowService(BugService bugService,
                              UserRepository userRepository,
                              CurrentUserService currentUserService,
                              AuditService auditService,
                              NotificationService notificationService) {
        this.bugService = bugService;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @Transactional
    public void assign(Long bugId, Long assigneeId) {
        Bug bug = bugService.getById(bugId);
        User assignee = userRepository.findById(assigneeId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + assigneeId));
        Long oldAssignee = idOf(bug.getAssignedTo());
        bug.setAssignedTo(assignee);
        bug.setAssignedAt(LocalDateTime.now());
        perform(bug, BugStatus.ASSIGNED, "ASSIGN", oldAssignee, assigneeId,
                "Assigned to " + assignee.displayLabel(), true, false, false);
    }

    @Transactional
    public void startAnalysis(Long bugId) {
        Bug bug = bugService.getById(bugId);
        perform(bug, BugStatus.IN_ANALYSIS, "START_ANALYSIS", idOf(bug.getAssignedTo()), idOf(bug.getAssignedTo()),
                "Started analysis", false, false, false);
    }

    @Transactional
    public void startDevelopment(Long bugId) {
        Bug bug = bugService.getById(bugId);
        perform(bug, BugStatus.IN_DEVELOPMENT, "START_DEVELOPMENT", idOf(bug.getAssignedTo()), idOf(bug.getAssignedTo()),
                "Started development", false, false, false);
    }

    @Transactional
    public void markFixed(Long bugId) {
        Bug bug = bugService.getById(bugId);
        bug.setFixedAt(LocalDateTime.now());
        perform(bug, BugStatus.FIXED, "MARK_FIXED", idOf(bug.getAssignedTo()), idOf(bug.getAssignedTo()),
                "Marked as fixed", false, false, false);
        notificationService.notifyBugFixed(bug);
    }

    @Transactional
    public void readyForRetest(Long bugId) {
        Bug bug = bugService.getById(bugId);
        perform(bug, BugStatus.READY_FOR_RETEST, "READY_FOR_RETEST", idOf(bug.getAssignedTo()), idOf(bug.getAssignedTo()),
                "Submitted for retest", false, true, false);
    }

    @Transactional
    public void startRetest(Long bugId) {
        Bug bug = bugService.getById(bugId);
        perform(bug, BugStatus.RETESTING, "START_RETEST", idOf(bug.getAssignedTo()), idOf(bug.getAssignedTo()),
                "Retesting started", false, false, false);
    }

    @Transactional
    public void retest(Long bugId, RetestForm form) {
        Bug bug = bugService.getById(bugId);
        if (bug.getStatus() != BugStatus.RETESTING) {
            throw new WorkflowException("Bug must be in RETESTING status to record a retest result");
        }

        bug.setRetestedAt(LocalDateTime.now());
        String result = form.getResult() == null ? "" : form.getResult().trim().toUpperCase();

        if (result.equals("PASS")) {
            bug.setClosedAt(LocalDateTime.now());
            perform(bug, BugStatus.CLOSED, "RETEST_PASS", idOf(bug.getAssignedTo()), idOf(bug.getAssignedTo()),
                    form.getComment(), false, false, false);
        } else if (result.equals("FAIL") || result.equals("BLOCKED")) {
            perform(bug, BugStatus.REOPENED, "RETEST_FAIL", idOf(bug.getAssignedTo()), idOf(bug.getAssignedTo()),
                    form.getComment(), false, false, true);
        } else {
            throw new WorkflowException("Retest result must be PASS, FAIL or BLOCKED");
        }

        if (form.getComment() != null && !form.getComment().isBlank()) {
            CommentForm commentForm = new CommentForm();
            commentForm.setComment(form.getComment());
            commentForm.setCommentType(CommentType.RETEST);
            bugService.addComment(bugId, commentForm);
        }
    }

    @Transactional
    public void reject(Long bugId, Resolution resolution, String comment) {
        Bug bug = bugService.getById(bugId);
        bug.setResolution(resolution);
        bug.setResolutionComment(comment);
        bug.setClosedAt(LocalDateTime.now());
        perform(bug, BugStatus.REJECTED, "REJECT", idOf(bug.getAssignedTo()), idOf(bug.getAssignedTo()),
                "Rejected: " + resolution, false, false, false);

        if (comment != null && !comment.isBlank()) {
            CommentForm commentForm = new CommentForm();
            commentForm.setComment(comment);
            commentForm.setCommentType(CommentType.RESOLUTION);
            bugService.addComment(bugId, commentForm);
        }
    }

    private void perform(Bug bug, BugStatus target, String action,
                         Long oldAssignee, Long newAssignee, String comment,
                         boolean notifyAssigned, boolean notifyReady, boolean notifyReopened) {
        BugStatus current = bug.getStatus();
        validateTransition(current, target);
        authorize(current, target);

        bug.setStatus(target);
        bugService.addHistory(bug, action, current, target, oldAssignee, newAssignee, comment);
        auditService.record(action, "BUG", bug.getId(), current.name(), target.name());

        if (notifyAssigned) {
            notificationService.notifyBugAssigned(bug);
        }
        if (notifyReady) {
            notificationService.notifyBugReadyForRetest(bug);
        }
        if (notifyReopened) {
            notificationService.notifyBugReopened(bug);
        }
    }

    private void validateTransition(BugStatus current, BugStatus target) {
        if (current == target) {
            throw new WorkflowException("Bug is already in status " + target);
        }
        if (!ALLOWED.getOrDefault(current, Set.of()).contains(target)) {
            throw new WorkflowException("Invalid bug transition: " + current + " -> " + target);
        }
    }

    private void authorize(BugStatus current, BugStatus target) {
        if (currentUserService.hasRole(Role.ADMIN)) {
            return;
        }
        boolean developer = currentUserService.hasRole(Role.DEVELOPER);
        boolean qaTester = currentUserService.hasRole(Role.QA_TESTER);
        boolean qaLead = currentUserService.hasRole(Role.QA_LEAD);
        boolean saLead = currentUserService.hasRole(Role.SA_LEAD);

        if (target == BugStatus.ASSIGNED && !qaTester && !qaLead) {
            throw new WorkflowException("Only QA users can assign a bug");
        }
        if (target == BugStatus.REJECTED && !qaLead) {
            throw new WorkflowException("Only QA Lead can reject a bug");
        }
        boolean developerTransitions = target == BugStatus.IN_ANALYSIS
                || target == BugStatus.IN_DEVELOPMENT
                || target == BugStatus.FIXED;
        if (developerTransitions && !developer) {
            throw new WorkflowException("Only developers can perform this transition");
        }
        if (target == BugStatus.READY_FOR_RETEST && !qaLead && !saLead) {
            throw new WorkflowException("Only QA Lead or SA Lead can mark a bug ready for retest");
        }
        boolean qaTransitions = target == BugStatus.RETESTING
                || target == BugStatus.CLOSED
                || target == BugStatus.REOPENED;
        if (qaTransitions && !qaTester && !qaLead) {
            throw new WorkflowException("Only QA users can perform this transition");
        }
    }

    private Long idOf(User user) {
        return user == null ? null : user.getId();
    }
}

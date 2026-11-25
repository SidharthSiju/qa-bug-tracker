package com.duc.crqa.cr.service;

import com.duc.crqa.audit.service.AuditService;
import com.duc.crqa.common.enums.Role;
import com.duc.crqa.common.enums.TestCaseReviewStage;
import com.duc.crqa.common.enums.TestCaseReviewStatus;
import com.duc.crqa.common.exception.WorkflowException;
import com.duc.crqa.cr.entity.ChangeRequest;
import com.duc.crqa.cr.repository.ChangeRequestRepository;
import com.duc.crqa.notification.service.NotificationService;
import com.duc.crqa.security.CurrentUserService;
import com.duc.crqa.testcase.repository.TestCaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TestCaseReviewService {

    private static final String ACTIVITY_TYPE = "TC_REVIEW";

    private final ChangeRequestRepository changeRequestRepository;
    private final ChangeRequestService changeRequestService;
    private final CurrentUserService currentUserService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final TestCaseRepository testCaseRepository;

    public TestCaseReviewService(ChangeRequestRepository changeRequestRepository,
                                 ChangeRequestService changeRequestService,
                                 CurrentUserService currentUserService,
                                 NotificationService notificationService,
                                 AuditService auditService,
                                 TestCaseRepository testCaseRepository) {
        this.changeRequestRepository = changeRequestRepository;
        this.changeRequestService = changeRequestService;
        this.currentUserService = currentUserService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.testCaseRepository = testCaseRepository;
    }

    @Transactional
    public void send(Long crId, TestCaseReviewStage stage) {
        ChangeRequest cr = changeRequestService.getById(crId);
        if (testCaseRepository.countByCrId(crId) == 0) {
            throw new WorkflowException("Add at least one test case before sending for review");
        }
        if (statusOf(cr) == TestCaseReviewStatus.CLOSED) {
            throw new WorkflowException("The test case review is closed. Reopen it before sending again.");
        }
        if (!canSend(cr)) {
            throw new WorkflowException("You are not authorized to send the test case review");
        }
        TestCaseReviewStatus target = stage.pendingStatus();
        String old = statusOf(cr).name();
        cr.setTcReviewStatus(target);
        changeRequestRepository.save(cr);

        changeRequestService.addActivity(cr, ACTIVITY_TYPE, "ready for " + stage.activityLabel() + " review");
        auditService.record("TC_REVIEW_SENT", "CHANGE_REQUEST", crId, old, target.name());
        notificationService.notifyTestCaseReview(cr, stage);
    }

    @Transactional
    public void confirm(Long crId) {
        ChangeRequest cr = changeRequestService.getById(crId);
        TestCaseReviewStage stage = statusOf(cr).stage();
        if (stage == null) {
            throw new WorkflowException("There is no test case review pending confirmation");
        }
        if (!canConfirm(cr)) {
            throw new WorkflowException("You are not authorized to confirm this test case review");
        }
        String old = statusOf(cr).name();
        TestCaseReviewStatus next = statusOf(cr).next();
        cr.setTcReviewStatus(next);
        changeRequestRepository.save(cr);

        changeRequestService.addActivity(cr, ACTIVITY_TYPE, stage.activityLabel() + " reviewed");
        auditService.record("TC_REVIEW_CONFIRMED", "CHANGE_REQUEST", crId, old, next.name());
    }

    @Transactional
    public void reopen(Long crId) {
        ChangeRequest cr = changeRequestService.getById(crId);
        if (statusOf(cr) != TestCaseReviewStatus.CLOSED) {
            throw new WorkflowException("Only a closed test case review can be reopened");
        }
        if (!canReopen(cr)) {
            throw new WorkflowException("You are not authorized to reopen the test case review");
        }
        String old = statusOf(cr).name();
        cr.setTcReviewStatus(TestCaseReviewStatus.PENDING_QA_LEAD);
        changeRequestRepository.save(cr);

        changeRequestService.addActivity(cr, ACTIVITY_TYPE, "Test case review reopened");
        auditService.record("TC_REVIEW_REOPENED", "CHANGE_REQUEST", crId, old,
                TestCaseReviewStatus.PENDING_QA_LEAD.name());
    }

    @Transactional(readOnly = true)
    public boolean canSend(ChangeRequest cr) {
        if (statusOf(cr) == TestCaseReviewStatus.CLOSED) {
            return false;
        }
        return currentUserService.hasAnyRole(Role.ADMIN, Role.QA_LEAD);
    }

    @Transactional(readOnly = true)
    public boolean canConfirm(ChangeRequest cr) {
        TestCaseReviewStage stage = statusOf(cr).stage();
        if (stage == null) {
            return false;
        }
        if (currentUserService.hasRole(Role.ADMIN)) {
            return true;
        }
        return switch (stage) {
            case SA_LEAD -> currentUserService.hasRole(Role.SA_LEAD);
            case QA_LEAD, DDM, BUSINESS -> currentUserService.hasRole(Role.QA_LEAD);
        };
    }

    @Transactional(readOnly = true)
    public boolean canReopen(ChangeRequest cr) {
        if (statusOf(cr) != TestCaseReviewStatus.CLOSED) {
            return false;
        }
        return currentUserService.hasAnyRole(Role.ADMIN, Role.QA_LEAD);
    }

    private TestCaseReviewStatus statusOf(ChangeRequest cr) {
        return cr.getTcReviewStatus() == null ? TestCaseReviewStatus.NOT_SUBMITTED : cr.getTcReviewStatus();
    }
}

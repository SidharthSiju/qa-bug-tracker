package com.duc.crqa.cr.service;

import com.duc.crqa.audit.service.AuditService;
import com.duc.crqa.common.enums.CrStatus;
import com.duc.crqa.common.enums.Role;
import com.duc.crqa.common.exception.WorkflowException;
import com.duc.crqa.cr.entity.ChangeRequest;
import com.duc.crqa.cr.repository.ChangeRequestRepository;
import com.duc.crqa.notification.service.NotificationService;
import com.duc.crqa.security.CurrentUserService;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CrWorkflowService {

    private static final Map<CrStatus, Set<CrStatus>> ALLOWED = Map.of(
            CrStatus.CREATED, Set.of(CrStatus.DEVELOPMENT, CrStatus.CANCELLED),
            CrStatus.DEVELOPMENT, Set.of(CrStatus.READY_FOR_QA, CrStatus.CANCELLED),
            CrStatus.READY_FOR_QA, Set.of(CrStatus.QA_IN_PROGRESS, CrStatus.CANCELLED),
            CrStatus.QA_IN_PROGRESS, Set.of(CrStatus.QA_BLOCKED, CrStatus.QA_COMPLETED, CrStatus.CANCELLED),
            CrStatus.QA_BLOCKED, Set.of(CrStatus.QA_IN_PROGRESS, CrStatus.CANCELLED),
            CrStatus.QA_COMPLETED, Set.of(CrStatus.UAT, CrStatus.CANCELLED),
            CrStatus.UAT, Set.of(CrStatus.COMPLETED, CrStatus.CANCELLED),
            CrStatus.COMPLETED, Set.of(),
            CrStatus.CANCELLED, Set.of()
    );

    private final ChangeRequestRepository changeRequestRepository;
    private final ChangeRequestService changeRequestService;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public CrWorkflowService(ChangeRequestRepository changeRequestRepository,
                             ChangeRequestService changeRequestService,
                             CurrentUserService currentUserService,
                             AuditService auditService,
                             NotificationService notificationService) {
        this.changeRequestRepository = changeRequestRepository;
        this.changeRequestService = changeRequestService;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @Transactional
    public void transition(Long crId, CrStatus target) {
        ChangeRequest cr = changeRequestService.getById(crId);
        CrStatus current = cr.getStatus();

        if (current == target) {
            throw new WorkflowException("Change request is already in status " + target);
        }
        if (!ALLOWED.getOrDefault(current, Set.of()).contains(target)) {
            throw new WorkflowException("Invalid change request transition: " + current + " -> " + target);
        }
        authorize(current, target);

        cr.setStatus(target);
        if (target == CrStatus.QA_IN_PROGRESS && cr.getActualStartDate() == null) {
            cr.setActualStartDate(LocalDateTime.now());
        }
        if (target == CrStatus.COMPLETED && cr.getActualEndDate() == null) {
            cr.setActualEndDate(LocalDateTime.now());
        }
        cr.setUpdatedBy(currentUserService.currentUserId());

        changeRequestRepository.save(cr);
        changeRequestService.addActivity(cr, "STATUS_CHANGED", current + " -> " + target);
        auditService.record("CR_STATUS_CHANGED", "CHANGE_REQUEST", cr.getId(), current.name(), target.name());

        if (target == CrStatus.READY_FOR_QA) {
            notificationService.notifyCrReadyForQa(cr);
        } else if (target == CrStatus.COMPLETED) {
            notificationService.notifyCrCompleted(cr);
        }
    }

    private void authorize(CrStatus current, CrStatus target) {
        boolean admin = currentUserService.hasRole(Role.ADMIN);
        if (admin) {
            return;
        }

        boolean crManager = currentUserService.hasRole(Role.CR_MANAGER);
        boolean qaLead = currentUserService.hasRole(Role.QA_LEAD);
        boolean saLead = currentUserService.hasRole(Role.SA_LEAD);

        if (target == CrStatus.CANCELLED) {
            if (!(crManager || qaLead)) {
                throw new WorkflowException("Only CR Manager, QA Lead or Admin can cancel a change request");
            }
            return;
        }

        boolean developmentTransitions = (current == CrStatus.CREATED && target == CrStatus.DEVELOPMENT)
                || (current == CrStatus.DEVELOPMENT && target == CrStatus.READY_FOR_QA);

        boolean completionTransitions = current == CrStatus.UAT && target == CrStatus.COMPLETED;

        boolean qaTransitions = (current == CrStatus.READY_FOR_QA && target == CrStatus.QA_IN_PROGRESS)
                || (current == CrStatus.QA_IN_PROGRESS && target == CrStatus.QA_BLOCKED)
                || (current == CrStatus.QA_BLOCKED && target == CrStatus.QA_IN_PROGRESS)
                || (current == CrStatus.QA_IN_PROGRESS && target == CrStatus.QA_COMPLETED)
                || (current == CrStatus.QA_COMPLETED && target == CrStatus.UAT);

        if (developmentTransitions && !(crManager || saLead || qaLead)) {
            throw new WorkflowException("You are not authorized to perform this change request transition");
        }
        if (completionTransitions && !(crManager || qaLead)) {
            throw new WorkflowException("You are not authorized to perform this change request transition");
        }
        if (qaTransitions && !qaLead) {
            throw new WorkflowException("You are not authorized to perform this change request transition");
        }
        if (!developmentTransitions && !completionTransitions && !qaTransitions) {
            throw new WorkflowException("You are not authorized to perform this change request transition");
        }
    }
}

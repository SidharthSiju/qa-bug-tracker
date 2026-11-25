package com.duc.crqa.notification.service;

import com.duc.crqa.bug.entity.Bug;
import com.duc.crqa.common.enums.Role;
import com.duc.crqa.common.enums.Severity;
import com.duc.crqa.common.enums.SlaStatus;
import com.duc.crqa.common.enums.TestCaseReviewStage;
import com.duc.crqa.cr.entity.ChangeRequest;
import com.duc.crqa.notification.entity.Notification;
import com.duc.crqa.notification.repository.NotificationRepository;
import com.duc.crqa.user.entity.User;
import com.duc.crqa.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final EmailNotifier emailNotifier;

    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                   UserRepository userRepository,
                                   EmailNotifier emailNotifier) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.emailNotifier = emailNotifier;
    }

    @Override
    @Transactional
    public void notifyBugCreated(Bug bug) {
        if (bug.getCr() == null) {
            return;
        }
        boolean critical = bug.getSeverity() == Severity.BLOCKER || bug.getSeverity() == Severity.CRITICAL;
        String type = critical ? "CRITICAL_BUG" : "BUG_CREATED";
        String subject = (critical ? "Critical bug created: " : "Bug created: ") + bug.getBugNumber();
        if (bug.getCr().getQaLead() != null) {
            create(bug.getCr().getQaLead(), type, subject, bug.getTitle(), "BUG", bug.getId());
        }
        if (bug.getCr().getSaLead() != null) {
            create(bug.getCr().getSaLead(), type, subject, bug.getTitle(), "BUG", bug.getId());
        }
    }

    @Override
    @Transactional
    public void notifyBugAssigned(Bug bug) {
        if (bug.getAssignedTo() != null) {
            create(bug.getAssignedTo(), "BUG_ASSIGNED", "Bug assigned to you: " + bug.getBugNumber(),
                    bug.getTitle(), "BUG", bug.getId());
        }
    }

    @Override
    @Transactional
    public void notifyBugFixed(Bug bug) {
        if (bug.getCr() == null) {
            return;
        }
        String subject = "Bug fixed - ready for retest review: " + bug.getBugNumber();
        if (bug.getCr().getQaLead() != null) {
            create(bug.getCr().getQaLead(), "BUG_FIXED", subject, bug.getTitle(), "BUG", bug.getId());
        }
        if (bug.getCr().getSaLead() != null) {
            create(bug.getCr().getSaLead(), "BUG_FIXED", subject, bug.getTitle(), "BUG", bug.getId());
        }
    }

    @Override
    @Transactional
    public void notifyBugReadyForRetest(Bug bug) {
        for (User tester : userRepository.findByRolesContainingAndActiveTrue(Role.QA_TESTER)) {
            create(tester, "READY_FOR_RETEST", "Bug ready for retest: " + bug.getBugNumber(),
                    bug.getTitle(), "BUG", bug.getId());
        }
    }

    @Override
    @Transactional
    public void notifyBugReopened(Bug bug) {
        if (bug.getAssignedTo() != null) {
            create(bug.getAssignedTo(), "BUG_REOPENED", "Bug reopened: " + bug.getBugNumber(),
                    bug.getTitle(), "BUG", bug.getId());
        }
    }

    @Override
    @Transactional
    public void notifySlaBreach(Bug bug, SlaStatus slaStatus) {
        String subject = "SLA " + slaStatus.name().toLowerCase().replace('_', ' ') + ": " + bug.getBugNumber();
        String message = "This " + bug.getSeverity() + " bug has been open past its "
                + slaStatus.name().toLowerCase().replace('_', ' ') + " SLA window.";

        // Whoever is involved gets one notification, even when the same person holds two of the roles.
        Set<Long> notified = new HashSet<>();
        for (User recipient : slaRecipients(bug)) {
            if (recipient != null && notified.add(recipient.getId())) {
                create(recipient, "BUG_SLA_BREACH", subject, message, "BUG", bug.getId());
            }
        }
    }

    private static List<User> slaRecipients(Bug bug) {
        List<User> recipients = new ArrayList<>();
        recipients.add(bug.getAssignedTo());
        if (bug.getCr() != null) {
            recipients.add(bug.getCr().getQaLead());
            recipients.add(bug.getCr().getSaLead());
        }
        return recipients;
    }

    @Override
    @Transactional
    public void notifyCrReadyForQa(ChangeRequest cr) {
        if (cr.getQaLead() != null) {
            create(cr.getQaLead(), "CR_READY_FOR_QA", "CR ready for QA: " + cr.getCrNumber(),
                    cr.getCrName(), "CR", cr.getId());
        }
    }

    @Override
    @Transactional
    public void notifyCrCompleted(ChangeRequest cr) {
        if (cr.getQaLead() != null) {
            create(cr.getQaLead(), "CR_COMPLETED", "CR completed: " + cr.getCrNumber(),
                    cr.getCrName(), "CR", cr.getId());
        }
        if (cr.getCreatedBy() != null) {
            userRepository.findById(cr.getCreatedBy()).ifPresent(createdBy ->
                    create(createdBy, "CR_COMPLETED", "CR completed: " + cr.getCrNumber(),
                            cr.getCrName(), "CR", cr.getId()));
        }
    }

    @Override
    @Transactional
    public void notifyCrUpdatedFromJira(ChangeRequest cr) {
        String subject = "Change request updated from JIRA: " + cr.getCrNumber();
        String message = "The JIRA import refreshed this change request (" + cr.getCrName() + ").";
        if (cr.getQaLead() != null) {
            create(cr.getQaLead(), "CR_UPDATED_FROM_JIRA", subject, message, "CR", cr.getId());
        }
        if (cr.getSaLead() != null) {
            create(cr.getSaLead(), "CR_UPDATED_FROM_JIRA", subject, message, "CR", cr.getId());
        }
    }

    @Override
    @Transactional
    public void notifyTestCaseReview(ChangeRequest cr, TestCaseReviewStage stage) {
        User recipient = stage == TestCaseReviewStage.SA_LEAD ? cr.getSaLead() : cr.getQaLead();
        if (recipient == null) {
            return;
        }
        create(recipient, "TC_REVIEW", "Test case review (" + stage.label() + "): " + cr.getCrNumber(),
                "Test cases for " + cr.getCrNumber() + " are ready for " + stage.label() + " review.",
                "CR", cr.getId());
    }

    private void create(User recipient, String type, String subject, String message,
                        String referenceType, Long referenceId) {
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setType(type);
        notification.setSubject(subject);
        notification.setMessage(message);
        notification.setReferenceType(referenceType);
        notification.setReferenceId(referenceId);
        notificationRepository.save(notification);

        // A user who opted out keeps the in-app notification but is not emailed.
        if (recipient.isEmailNotifications() && recipient.getEmail() != null && !recipient.getEmail().isBlank()) {
            emailNotifier.send(recipient.getEmail(), subject, emailBody(message));
        }
    }

    /** The in-app message plus a short footer, so a mail read out of context still says where it came from. */
    private static String emailBody(String message) {
        String body = message == null || message.isBlank() ? "You have a new notification." : message;
        return body + "\n\n--\nCR Issue Tracker - sign in to see the details.";
    }
}

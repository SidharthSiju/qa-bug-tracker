package com.duc.crqa.bug.service;

import com.duc.crqa.audit.service.AuditService;
import com.duc.crqa.bug.entity.Bug;
import com.duc.crqa.bug.repository.BugRepository;
import com.duc.crqa.common.enums.SlaStatus;
import com.duc.crqa.config.SlaProperties;
import com.duc.crqa.notification.service.NotificationService;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notifies the owners of bugs that have passed their SLA window.
 *
 * <p>Switched off until an administrator turns it on ({@code app.sla.escalation-enabled}, editable at runtime with
 * Reload Config). A bug is escalated once per {@code app.sla.escalate-every-hours} window, so nobody is reminded of the
 * same defect every day.
 */
@Service
public class SlaEscalationService {

    private static final Logger log = LoggerFactory.getLogger(SlaEscalationService.class);

    private final BugService bugService;
    private final BugRepository bugRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final SlaProperties slaProperties;

    public SlaEscalationService(BugService bugService,
                                BugRepository bugRepository,
                                NotificationService notificationService,
                                AuditService auditService,
                                SlaProperties slaProperties) {
        this.bugService = bugService;
        this.bugRepository = bugRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.slaProperties = slaProperties;
    }

    /** The scheduled sweep. The cron itself is fixed at start-up; the switch and window are read on every run. */
    @Scheduled(cron = "${app.sla.escalation-cron:0 0 7 * * *}")
    @Transactional
    public void escalateScheduled() {
        if (!slaProperties.isEscalationEnabled()) {
            log.debug("SLA escalation is switched off");
            return;
        }
        int escalated = escalateNow();
        if (escalated > 0) {
            log.info("SLA escalation notified the owners of {} bug(s)", escalated);
        }
    }

    /**
     * Runs the sweep immediately (used by the scheduled job and by the "Run escalation now" action on the SLA report).
     *
     * @return how many bugs were escalated
     */
    @Transactional
    public int escalateNow() {
        LocalDateTime cutoff = LocalDateTime.now()
                .minusHours(Math.max(1, slaProperties.getEscalateEveryHours()));
        int escalated = 0;

        for (Bug bug : bugService.findBySla(SlaStatus.BREACHED)) {
            if (bug.getSlaEscalatedAt() != null && bug.getSlaEscalatedAt().isAfter(cutoff)) {
                continue;
            }
            notificationService.notifySlaBreach(bug, SlaStatus.BREACHED);
            bug.setSlaEscalatedAt(LocalDateTime.now());
            bugRepository.save(bug);
            auditService.record("BUG_SLA_ESCALATED", "BUG", bug.getId(), null, bug.getBugNumber());
            escalated++;
        }
        return escalated;
    }
}

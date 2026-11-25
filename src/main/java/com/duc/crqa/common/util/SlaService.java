package com.duc.crqa.common.util;

import com.duc.crqa.bug.entity.Bug;
import com.duc.crqa.common.enums.BugStatus;
import com.duc.crqa.common.enums.Severity;
import com.duc.crqa.common.enums.SlaStatus;
import com.duc.crqa.config.SlaProperties;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class SlaService {

    private final SlaProperties slaProperties;

    public SlaService(SlaProperties slaProperties) {
        this.slaProperties = slaProperties;
    }

    public Duration slaFor(Severity severity) {
        int hours = switch (severity) {
            case BLOCKER -> slaProperties.getBlockerHours();
            case CRITICAL -> slaProperties.getCriticalHours();
            case MAJOR -> slaProperties.getMajorHours();
            case MINOR -> slaProperties.getMinorHours();
            case TRIVIAL -> slaProperties.getTrivialHours();
        };
        return Duration.ofHours(hours);
    }

    public LocalDateTime deadline(Bug bug) {
        return bug.getReportedAt().plus(slaFor(bug.getSeverity()));
    }

    public SlaStatus status(Bug bug) {
        if (bug.getStatus() == BugStatus.CLOSED || bug.getStatus() == BugStatus.REJECTED) {
            return SlaStatus.WITHIN;
        }
        Duration sla = slaFor(bug.getSeverity());
        Duration open = Duration.between(bug.getReportedAt(), LocalDateTime.now());
        if (!open.isNegative() && open.compareTo(sla) >= 0) {
            return SlaStatus.BREACHED;
        }
        double ratio = (double) open.toMinutes() / Math.max(1, sla.toMinutes());
        if (ratio >= 0.75) {
            return SlaStatus.APPROACHING;
        }
        return SlaStatus.WITHIN;
    }
}

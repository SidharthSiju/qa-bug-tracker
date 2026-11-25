package com.duc.crqa.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.sla")
@Getter
@Setter
public class SlaProperties {

    private int blockerHours = 4;
    private int criticalHours = 8;
    private int majorHours = 48;
    private int minorHours = 120;
    private int trivialHours = 240;

    /** When true, the scheduled sweep notifies the owners of bugs that are past their SLA. Off until switched on. */
    private boolean escalationEnabled = false;

    /** Cron for the escalation sweep. Changing it needs a restart; the switch and window are picked up live. */
    private String escalationCron = "0 0 7 * * *";

    /** Do not escalate the same bug again within this many hours. */
    private int escalateEveryHours = 24;
}

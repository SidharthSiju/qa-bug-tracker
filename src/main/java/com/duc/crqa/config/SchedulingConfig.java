package com.duc.crqa.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the scheduled jobs (currently the SLA escalation sweep). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}

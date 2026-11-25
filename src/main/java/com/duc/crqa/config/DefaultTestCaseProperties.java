package com.duc.crqa.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Controls whether a new change request gets the auto-generated defaults (a "Default test cycle" with a
 * "Default test case"). Bound to {@code app.default-test-case.*} in application.yml.
 */
@Component
@ConfigurationProperties(prefix = "app.default-test-case")
@Getter
@Setter
public class DefaultTestCaseProperties {

    private boolean enabled = true;
}

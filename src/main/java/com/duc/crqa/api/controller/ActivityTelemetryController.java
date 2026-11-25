package com.duc.crqa.api.controller;

import com.duc.crqa.analytics.dto.ActivityPing;
import com.duc.crqa.analytics.service.UserActivityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/activity")
public class ActivityTelemetryController {

    private final UserActivityService userActivityService;

    public ActivityTelemetryController(UserActivityService userActivityService) {
        this.userActivityService = userActivityService;
    }

    @PostMapping
    public ResponseEntity<Void> record(@RequestBody ActivityPing ping) {
        userActivityService.record(ping);
        return ResponseEntity.noContent().build();
    }
}

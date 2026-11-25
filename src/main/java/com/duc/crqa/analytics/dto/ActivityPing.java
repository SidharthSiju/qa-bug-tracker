package com.duc.crqa.analytics.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ActivityPing {

    private String path;

    private long seconds;

    private Long startedAtEpochMs;
}

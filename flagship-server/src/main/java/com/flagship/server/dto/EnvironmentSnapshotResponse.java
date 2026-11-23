package com.flagship.server.dto;

import java.time.Instant;
import java.util.Map;

public record EnvironmentSnapshotResponse(
        String projectKey,
        String environmentKey,
        long revision,
        Instant generatedAt,
        Map<String, FlagResponse> flags,
        Map<String, SegmentResponse> segments
) {
}

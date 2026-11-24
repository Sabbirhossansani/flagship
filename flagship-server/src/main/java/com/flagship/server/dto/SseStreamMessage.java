package com.flagship.server.dto;

import java.time.Instant;

public record SseStreamMessage(
        String eventType,
        String projectKey,
        String envKey,
        String entityType,
        String entityKey,
        Long revision,
        Instant timestamp,
        Object data
) {
}

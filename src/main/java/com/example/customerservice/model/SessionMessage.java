package com.example.customerservice.model;

import java.time.Instant;
import java.util.Map;

public record SessionMessage(
        String messageId,
        String sessionId,
        SessionRole role,
        String content,
        Instant createdAt,
        Map<String, String> metadata
) {
    public SessionMessage {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}

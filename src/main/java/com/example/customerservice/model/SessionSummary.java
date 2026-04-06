package com.example.customerservice.model;

import java.time.Instant;
import java.util.Objects;

public record SessionSummary(
        String sessionId,
        int version,
        String topic,
        String summary,
        Instant updatedAt
) {
    public boolean hasContent() {
        return !Objects.toString(topic, "").isBlank() || !Objects.toString(summary, "").isBlank();
    }
}

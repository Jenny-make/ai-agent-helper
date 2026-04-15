package com.example.customerservice.model;

import java.time.Instant;
import java.util.Objects;

public record ActiveDocument(
        String sessionId,
        String documentId,
        String title,
        String source,
        String sourceToken,
        Instant updatedAt
) {
    public ActiveDocument {
        sessionId = Objects.toString(sessionId, "").trim();
        documentId = Objects.toString(documentId, "").trim();
        title = Objects.toString(title, "").trim();
        source = Objects.toString(source, "").trim();
        sourceToken = Objects.toString(sourceToken, "").trim();
        updatedAt = updatedAt == null ? Instant.now() : updatedAt;
    }

    public boolean hasFilterKey() {
        return !sourceToken.isBlank() || !source.isBlank() || !documentId.isBlank();
    }
}

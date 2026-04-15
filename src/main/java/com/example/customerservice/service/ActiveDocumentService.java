package com.example.customerservice.service;

import com.example.customerservice.model.ActiveDocument;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Service;

@Service
public class ActiveDocumentService {

    private final JdbcOperations jdbcOperations;
    private final ConcurrentMap<String, ActiveDocument> activeDocuments = new ConcurrentHashMap<>();

    public ActiveDocumentService(ObjectProvider<JdbcOperations> jdbcOperationsProvider) {
        this.jdbcOperations = jdbcOperationsProvider == null ? null : jdbcOperationsProvider.getIfAvailable();
    }

    ActiveDocumentService(JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations;
    }

    public Optional<ActiveDocument> getActiveDocument(String sessionId) {
        String normalizedSessionId = normalizeSessionId(sessionId);
        if (normalizedSessionId.isBlank()) {
            return Optional.empty();
        }
        if (databaseBacked()) {
            return loadPersistentActiveDocument(normalizedSessionId);
        }
        return Optional.ofNullable(activeDocuments.get(normalizedSessionId));
    }

    public void updateActiveDocument(ActiveDocument activeDocument) {
        if (activeDocument == null || activeDocument.sessionId().isBlank() || activeDocument.documentId().isBlank()) {
            return;
        }
        if (databaseBacked()) {
            jdbcOperations.update(
                    """
                    insert into active_document
                        (session_id, document_id, title, source, source_token, updated_at)
                    values (?, ?, ?, ?, ?, current_timestamp(6))
                    on duplicate key update
                        document_id = values(document_id),
                        title = values(title),
                        source = values(source),
                        source_token = values(source_token),
                        updated_at = current_timestamp(6)
                    """,
                    activeDocument.sessionId(),
                    activeDocument.documentId(),
                    emptyToNull(activeDocument.title()),
                    emptyToNull(activeDocument.source()),
                    emptyToNull(activeDocument.sourceToken())
            );
            return;
        }
        activeDocuments.put(activeDocument.sessionId(), activeDocument);
    }

    public void clearActiveDocument(String sessionId) {
        String normalizedSessionId = normalizeSessionId(sessionId);
        if (normalizedSessionId.isBlank()) {
            return;
        }
        if (databaseBacked()) {
            jdbcOperations.update("delete from active_document where session_id = ?", normalizedSessionId);
            return;
        }
        activeDocuments.remove(normalizedSessionId);
    }

    private Optional<ActiveDocument> loadPersistentActiveDocument(String sessionId) {
        try {
            return Optional.ofNullable(jdbcOperations.queryForObject(
                    """
                    select session_id, document_id, title, source, source_token, updated_at
                    from active_document
                    where session_id = ?
                    """,
                    (rs, rowNum) -> {
                        Timestamp updatedAt = rs.getTimestamp("updated_at");
                        return new ActiveDocument(
                                rs.getString("session_id"),
                                rs.getString("document_id"),
                                rs.getString("title"),
                                rs.getString("source"),
                                rs.getString("source_token"),
                                updatedAt == null ? Instant.now() : updatedAt.toInstant()
                        );
                    },
                    sessionId
            ));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private boolean databaseBacked() {
        return jdbcOperations != null;
    }

    private String normalizeSessionId(String sessionId) {
        return Objects.toString(sessionId, "").trim();
    }

    private String emptyToNull(String text) {
        String value = Objects.toString(text, "").trim();
        return value.isBlank() ? null : value;
    }
}

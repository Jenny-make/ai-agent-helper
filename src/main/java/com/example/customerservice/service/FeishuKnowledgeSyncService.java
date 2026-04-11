package com.example.customerservice.service;

import com.example.customerservice.config.FeishuKnowledgeProperties;
import com.example.customerservice.config.RagProperties;
import com.example.customerservice.model.knowledge.FeishuKnowledgeDocument;
import com.example.customerservice.model.knowledge.FeishuKnowledgeSource;
import com.example.customerservice.model.knowledge.FeishuKnowledgeSyncResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class FeishuKnowledgeSyncService {

    private static final Logger logger = LoggerFactory.getLogger(FeishuKnowledgeSyncService.class);
    private static final int MAX_VECTOR_DOC_ID_LENGTH = 32;

    private final FeishuKnowledgeProperties feishuKnowledgeProperties;
    private final RagProperties ragProperties;
    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final FeishuKnowledgeSourceClient feishuKnowledgeSourceClient;
    private final FeishuKnowledgeSourceDiscoveryService feishuKnowledgeSourceDiscoveryService;
    private final FeishuKnowledgeSyncStateStore stateStore;

    public FeishuKnowledgeSyncService(
            FeishuKnowledgeProperties feishuKnowledgeProperties,
            RagProperties ragProperties,
            ObjectProvider<VectorStore> vectorStoreProvider,
            FeishuKnowledgeSourceClient feishuKnowledgeSourceClient,
            FeishuKnowledgeSourceDiscoveryService feishuKnowledgeSourceDiscoveryService,
            FeishuKnowledgeSyncStateStore stateStore
    ) {
        this.feishuKnowledgeProperties = feishuKnowledgeProperties;
        this.ragProperties = ragProperties;
        this.vectorStoreProvider = vectorStoreProvider;
        this.feishuKnowledgeSourceClient = feishuKnowledgeSourceClient;
        this.feishuKnowledgeSourceDiscoveryService = feishuKnowledgeSourceDiscoveryService;
        this.stateStore = stateStore;
    }

    public FeishuKnowledgeSyncResult syncConfiguredSources() {
        return sync(feishuKnowledgeProperties.sourcesOrEmpty());
    }

    public FeishuKnowledgeSyncResult sync(List<FeishuKnowledgeSource> requestedSources) {
        if (!feishuKnowledgeProperties.enabled()) {
            throw new IllegalStateException("Feishu knowledge sync is disabled. Set APP_FEISHU_KNOWLEDGE_ENABLED=true.");
        }
        if (!ragProperties.enabled()) {
            throw new IllegalStateException("RAG is disabled. Set APP_RAG_ENABLED=true before syncing documents.");
        }

        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            throw new IllegalStateException("VectorStore bean is not available. Check Milvus/RAG configuration.");
        }

        List<FeishuKnowledgeSource> sources = feishuKnowledgeSourceDiscoveryService.expand(sanitizeSources(requestedSources));
        if (sources.isEmpty()) {
            throw new IllegalArgumentException(
                    "No Feishu knowledge sources were provided. Configure app.feishu.knowledge.sources or send sources in the sync request."
            );
        }

        List<FeishuKnowledgeSyncResult.SourceSyncResult> details = new ArrayList<>();
        int syncedSources = 0;
        int syncedChunks = 0;
        boolean success = true;

        for (FeishuKnowledgeSource source : sources) {
            try {
                FeishuKnowledgeDocument document = feishuKnowledgeSourceClient.fetch(source);
                List<String> chunks = chunkText(document.text());
                if (chunks.isEmpty()) {
                    details.add(new FeishuKnowledgeSyncResult.SourceSyncResult(
                            source.resolvedToken(),
                            source.resolvedType(),
                            firstNonBlank(document.title(), source.normalizedTitle(), source.resolvedToken()),
                            "skipped",
                            0,
                            "Fetched document text is empty after normalization."
                    ));
                    continue;
                }

                List<String> previousIds = stateStore.getDocumentIds(feishuKnowledgeProperties.stateFile(), source.sourceKey());
                deletePreviousDocuments(vectorStore, previousIds);

                List<Document> vectorDocuments = new ArrayList<>(chunks.size());
                List<String> newIds = new ArrayList<>(chunks.size());
                for (int i = 0; i < chunks.size(); i++) {
                    String chunk = chunks.get(i);
                    String id = buildDocumentId(source, i, document.text());
                    newIds.add(id);
                    vectorDocuments.add(Document.builder()
                            .id(id)
                            .text(chunk)
                            .metadata(buildMetadata(document, source, i, chunks.size()))
                            .build());
                }

                vectorStore.add(vectorDocuments);
                stateStore.update(feishuKnowledgeProperties.stateFile(), source.sourceKey(), document.title(), newIds);

                syncedSources++;
                syncedChunks += vectorDocuments.size();
                details.add(new FeishuKnowledgeSyncResult.SourceSyncResult(
                        source.resolvedToken(),
                        source.resolvedType(),
                        document.title(),
                        "synced",
                        vectorDocuments.size(),
                        "Synced successfully."
                ));
            } catch (Exception e) {
                success = false;
                logger.warn("feishu.knowledge.sync failed for source={} message={}", source.sourceKey(), e.getMessage(), e);
                details.add(new FeishuKnowledgeSyncResult.SourceSyncResult(
                        source.resolvedToken(),
                        source.resolvedType(),
                        firstNonBlank(source.normalizedTitle(), source.resolvedToken()),
                        "error",
                        0,
                        Objects.toString(e.getMessage(), e.getClass().getSimpleName())
                ));
            }
        }

        return new FeishuKnowledgeSyncResult(success, sources.size(), syncedSources, syncedChunks, details);
    }

    private List<FeishuKnowledgeSource> sanitizeSources(List<FeishuKnowledgeSource> requestedSources) {
        List<FeishuKnowledgeSource> sanitized = new ArrayList<>();
        List<FeishuKnowledgeSource> safeSources = requestedSources == null ? List.of() : requestedSources;
        for (FeishuKnowledgeSource source : safeSources) {
            if (source == null || source.resolvedToken().isBlank()) {
                continue;
            }
            sanitized.add(source);
        }
        return sanitized;
    }

    private void deletePreviousDocuments(VectorStore vectorStore, List<String> previousIds) {
        if (previousIds == null || previousIds.isEmpty()) {
            return;
        }
        try {
            vectorStore.delete(previousIds);
        } catch (UnsupportedOperationException e) {
            logger.warn("VectorStore delete is not supported; re-sync may create duplicates until state is reset.");
        }
    }

    private Map<String, Object> buildMetadata(
            FeishuKnowledgeDocument document,
            FeishuKnowledgeSource source,
            int chunkIndex,
            int totalChunks
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", document.sourceReference());
        metadata.put("title", document.title());
        metadata.put("sourceType", "feishu-" + source.resolvedType());
        metadata.put("sourceToken", source.resolvedToken());
        metadata.put("chunkIndex", chunkIndex);
        metadata.put("chunkCount", totalChunks);
        metadata.put("syncedAt", Instant.now().toString());
        return metadata;
    }

    private String buildDocumentId(FeishuKnowledgeSource source, int chunkIndex, String content) {
        // Keep Milvus document ids short and deterministic to fit common VarChar limits.
        String fingerprint = sha256Hex(source.sourceKey() + "|" + chunkIndex + "|" + content);
        return fingerprint.substring(0, MAX_VECTOR_DOC_ID_LENGTH);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Objects.toString(value, "").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash document fingerprint.", e);
        }
    }

    private List<String> chunkText(String text) {
        String normalized = Objects.toString(text, "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        if (normalized.isBlank()) {
            return List.of();
        }

        int chunkSize = feishuKnowledgeProperties.chunkSize();
        int overlap = Math.min(feishuKnowledgeProperties.chunkOverlap(), Math.max(chunkSize - 1, 0));
        int maxChunks = feishuKnowledgeProperties.maxChunksPerDocument();
        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < normalized.length() && chunks.size() < maxChunks) {
            int proposedEnd = Math.min(start + chunkSize, normalized.length());
            int end = findChunkEnd(normalized, start, proposedEnd);
            if (end <= start) {
                end = proposedEnd;
            }

            String chunk = normalized.substring(start, end).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }

            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }

        return chunks;
    }

    private int findChunkEnd(String text, int start, int proposedEnd) {
        if (proposedEnd >= text.length()) {
            return text.length();
        }
        int minBoundary = Math.min(start + Math.max(feishuKnowledgeProperties.chunkSize() / 2, 1), proposedEnd);
        String boundaries = "\n\u3002\uFF01\uFF1F.!?\uFF1B; ";
        for (int i = proposedEnd; i > minBoundary; i--) {
            if (boundaries.indexOf(text.charAt(i - 1)) >= 0) {
                return i;
            }
        }
        return proposedEnd;
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            String value = Objects.toString(candidate, "").trim();
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}

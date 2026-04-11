package com.example.customerservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.customerservice.config.FeishuKnowledgeProperties;
import com.example.customerservice.config.RagProperties;
import com.example.customerservice.model.knowledge.FeishuKnowledgeDocument;
import com.example.customerservice.model.knowledge.FeishuKnowledgeSource;
import com.example.customerservice.model.knowledge.FeishuKnowledgeSyncResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

class FeishuKnowledgeSyncServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void syncsDocxSourceIntoVectorStoreAndPersistsIds() {
        VectorStore vectorStore = mock(VectorStore.class);
        ObjectProvider<VectorStore> vectorStoreProvider = new StaticObjectProvider<>(vectorStore);

        FeishuKnowledgeSourceClient client = source -> new FeishuKnowledgeDocument(
                source.resolvedToken(),
                source.resolvedType(),
                "Refund Policy",
                "https://example.feishu.cn/docx/abc123",
                "Refund requests can be submitted within seven days.\n\n"
                        + "Please provide your order number and reason for refund."
        );

        FeishuKnowledgeSyncService service = new FeishuKnowledgeSyncService(
                new FeishuKnowledgeProperties(
                        true,
                        false,
                        120,
                        20,
                        10,
                        tempDir.resolve("sync-state.json").toString(),
                        List.of()
                ),
                new RagProperties(true, 4, 0.0, 1200),
                vectorStoreProvider,
                client,
                sources -> sources,
                new FeishuKnowledgeSyncStateStore(new ObjectMapper())
        );

        FeishuKnowledgeSyncResult result = service.sync(List.of(
                new FeishuKnowledgeSource("", "auto", "Refund Policy", "https://example.feishu.cn/docx/abc123")
        ));

        assertTrue(result.success());
        assertEquals(1, result.syncedSources());
        assertTrue(result.syncedChunks() >= 1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        List<Document> stored = captor.getValue();
        assertFalse(stored.isEmpty());
        assertTrue(stored.stream().allMatch(document -> document.getId().length() <= 32));
        assertEquals("Refund Policy", stored.get(0).getMetadata().get("title"));
        assertEquals("https://example.feishu.cn/docx/abc123", stored.get(0).getMetadata().get("source"));
        assertEquals("abc123", stored.get(0).getMetadata().get("sourceToken"));
    }

    @Test
    void reSyncDeletesPreviousChunkIdsBeforeAddingAgain() {
        VectorStore vectorStore = mock(VectorStore.class);
        ObjectProvider<VectorStore> vectorStoreProvider = new StaticObjectProvider<>(vectorStore);

        FeishuKnowledgeSourceClient client = source -> new FeishuKnowledgeDocument(
                source.resolvedToken(),
                source.resolvedType(),
                "Shipping FAQ",
                "feishu-docx:" + source.resolvedToken(),
                "Shipping usually takes three to five business days."
        );

        FeishuKnowledgeSyncService service = new FeishuKnowledgeSyncService(
                new FeishuKnowledgeProperties(
                        true,
                        false,
                        120,
                        20,
                        10,
                        tempDir.resolve("sync-state.json").toString(),
                        List.of()
                ),
                new RagProperties(true, 4, 0.0, 1200),
                vectorStoreProvider,
                client,
                sources -> sources,
                new FeishuKnowledgeSyncStateStore(new ObjectMapper())
        );

        FeishuKnowledgeSource source = new FeishuKnowledgeSource("doc-token", "docx", "Shipping FAQ", "");
        service.sync(List.of(source));
        service.sync(List.of(source));

        verify(vectorStore, times(2)).add(anyList());
        verify(vectorStore, times(1)).delete(anyList());
    }

    private static final class StaticObjectProvider<T> implements ObjectProvider<T> {

        private final T value;

        private StaticObjectProvider(T value) {
            this.value = value;
        }

        @Override
        public T getObject(Object... args) {
            return value;
        }

        @Override
        public T getIfAvailable() {
            return value;
        }

        @Override
        public T getIfUnique() {
            return value;
        }

        @Override
        public T getObject() {
            return value;
        }
    }
}

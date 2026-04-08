package com.example.customerservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class FeishuKnowledgeSyncStateStore {

    private final ObjectMapper objectMapper;

    public FeishuKnowledgeSyncStateStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public synchronized List<String> getDocumentIds(String stateFile, String sourceKey) {
        return readStateFile(stateFile).sources()
                .getOrDefault(sourceKey, StoredSourceState.empty())
                .documentIds();
    }

    public synchronized void update(String stateFile, String sourceKey, String title, List<String> documentIds) {
        StateFile stateFileContent = readStateFile(stateFile);
        Map<String, StoredSourceState> sources = new LinkedHashMap<>(stateFileContent.sources());
        sources.put(sourceKey, new StoredSourceState(
                List.copyOf(documentIds),
                Objects.toString(title, "").trim(),
                Instant.now().toString()
        ));
        writeStateFile(stateFile, new StateFile(sources));
    }

    private StateFile readStateFile(String stateFile) {
        Path path = resolvePath(stateFile);
        if (!Files.exists(path)) {
            return StateFile.empty();
        }
        try {
            StateFile content = objectMapper.readValue(path.toFile(), StateFile.class);
            return content == null || content.sources() == null ? StateFile.empty() : content;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read Feishu knowledge sync state file: " + path, e);
        }
    }

    private void writeStateFile(String stateFile, StateFile content) {
        Path path = resolvePath(stateFile);
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), content);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write Feishu knowledge sync state file: " + path, e);
        }
    }

    private Path resolvePath(String stateFile) {
        String configured = Objects.toString(stateFile, "").trim();
        String fallback = "build/feishu-knowledge/sync-state.json";
        return Paths.get(configured.isBlank() ? fallback : configured);
    }

    private record StateFile(Map<String, StoredSourceState> sources) {
        private static StateFile empty() {
            return new StateFile(Map.of());
        }
    }

    private record StoredSourceState(List<String> documentIds, String title, String syncedAt) {
        private static StoredSourceState empty() {
            return new StoredSourceState(List.of(), "", "");
        }
    }
}

package com.example.customerservice.config;

import com.example.customerservice.model.knowledge.FeishuKnowledgeSource;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.feishu.knowledge")
public record FeishuKnowledgeProperties(
        boolean enabled,
        boolean autoSyncOnStartup,
        @Min(100) int chunkSize,
        @Min(0) int chunkOverlap,
        @Min(1) int maxChunksPerDocument,
        String stateFile,
        List<FeishuKnowledgeSource> sources
) {
    public List<FeishuKnowledgeSource> sourcesOrEmpty() {
        return sources == null ? List.of() : sources;
    }
}

package com.example.customerservice.model.knowledge;

import jakarta.validation.Valid;
import java.util.List;

public record FeishuKnowledgeSyncRequest(
        List<@Valid FeishuKnowledgeSource> sources
) {
    public List<FeishuKnowledgeSource> sourcesOrEmpty() {
        return sources == null ? List.of() : sources;
    }
}

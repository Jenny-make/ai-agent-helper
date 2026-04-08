package com.example.customerservice.model.knowledge;

import java.util.List;

public record FeishuKnowledgeSyncResult(
        boolean success,
        int requestedSources,
        int syncedSources,
        int syncedChunks,
        List<SourceSyncResult> details
) {
    public record SourceSyncResult(
            String token,
            String type,
            String title,
            String status,
            int chunkCount,
            String message
    ) {
    }
}

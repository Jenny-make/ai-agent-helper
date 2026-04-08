package com.example.customerservice.controller;

import com.example.customerservice.model.knowledge.FeishuKnowledgeSyncRequest;
import com.example.customerservice.model.knowledge.FeishuKnowledgeSyncResult;
import com.example.customerservice.service.FeishuKnowledgeSyncService;
import com.example.customerservice.service.KnowledgeAnswerService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class KnowledgeAdminController {

    private final KnowledgeAnswerService knowledgeAnswerService;
    private final FeishuKnowledgeSyncService feishuKnowledgeSyncService;

    public KnowledgeAdminController(
            KnowledgeAnswerService knowledgeAnswerService,
            FeishuKnowledgeSyncService feishuKnowledgeSyncService
    ) {
        this.knowledgeAnswerService = knowledgeAnswerService;
        this.feishuKnowledgeSyncService = feishuKnowledgeSyncService;
    }

    @GetMapping("/health/vector")
    public ResponseEntity<Map<String, Object>> vectorHealth() {
        return ResponseEntity.ok(Map.of(
                "status", knowledgeAnswerService.vectorStoreReady() ? "ok" : "down"
        ));
    }

    @PostMapping("/knowledge/sync/feishu")
    public FeishuKnowledgeSyncResult syncFeishuKnowledge(
            @Valid @RequestBody(required = false) FeishuKnowledgeSyncRequest request
    ) {
        if (request == null || request.sourcesOrEmpty().isEmpty()) {
            return feishuKnowledgeSyncService.syncConfiguredSources();
        }
        return feishuKnowledgeSyncService.sync(request.sourcesOrEmpty());
    }
}

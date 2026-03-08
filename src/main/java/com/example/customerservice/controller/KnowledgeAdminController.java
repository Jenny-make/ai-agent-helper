package com.example.customerservice.controller;

import com.example.customerservice.service.KnowledgeAnswerService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class KnowledgeAdminController {

    private final KnowledgeAnswerService knowledgeAnswerService;

    public KnowledgeAdminController(KnowledgeAnswerService knowledgeAnswerService) {
        this.knowledgeAnswerService = knowledgeAnswerService;
    }

    @GetMapping("/health/vector")
    public ResponseEntity<Map<String, Object>> vectorHealth() {
        return ResponseEntity.ok(Map.of(
                "status", knowledgeAnswerService.vectorStoreReady() ? "ok" : "down"
        ));
    }
}

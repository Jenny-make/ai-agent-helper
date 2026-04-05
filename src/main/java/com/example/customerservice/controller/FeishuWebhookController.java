package com.example.customerservice.controller;

import com.example.customerservice.model.CustomerMessage;
import com.example.customerservice.model.ReplyResult;
import com.example.customerservice.model.feishu.FeishuWebhookRequest;
import com.example.customerservice.service.FeishuWebhookService;
import com.example.customerservice.service.KnowledgeAnswerService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feishu")
public class FeishuWebhookController {

    private final FeishuWebhookService feishuWebhookService;
    private final KnowledgeAnswerService knowledgeAnswerService;

    public FeishuWebhookController(
            FeishuWebhookService feishuWebhookService,
            KnowledgeAnswerService knowledgeAnswerService
    ) {
        this.feishuWebhookService = feishuWebhookService;
        this.knowledgeAnswerService = knowledgeAnswerService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> handleWebhook(@RequestBody FeishuWebhookRequest payload) {
        if (!feishuWebhookService.verifyToken(payload)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", 403, "msg", "invalid verification token"));
        }

        if (feishuWebhookService.isUrlVerification(payload)) {
            return ResponseEntity.ok(Map.of("challenge", feishuWebhookService.getChallenge(payload)));
        }

        feishuWebhookService.handleEvent(payload);
        return ResponseEntity.ok(Map.of("code", 0, "msg", "ok"));
    }

    @PostMapping("/debug/reply")
    public ReplyResult debugReply(@Valid @RequestBody CustomerMessage message) {
        return knowledgeAnswerService.answer(message);
    }
}

package com.example.customerservice.controller;

import com.example.customerservice.model.CustomerMessage;
import com.example.customerservice.model.ReplyResult;
import com.example.customerservice.service.KnowledgeAnswerService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feishu")
public class FeishuWebhookController {

    private final KnowledgeAnswerService knowledgeAnswerService;

    public FeishuWebhookController(KnowledgeAnswerService knowledgeAnswerService) {
        this.knowledgeAnswerService = knowledgeAnswerService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> handleWebhook(@RequestBody Map<String, Object> payload) {
        if ("url_verification".equals(payload.get("type"))) {
            return ResponseEntity.ok(Map.of("challenge", payload.get("challenge")));
        }

        CustomerMessage message = new CustomerMessage(
                "feishu-user",
                "feishu-session",
                String.valueOf(payload.getOrDefault("text", ""))
        );
        ReplyResult result = knowledgeAnswerService.answer(message);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "provider", result.provider(),
                "answer", result.answer()
        ));
    }

    @PostMapping("/debug/reply")
    public ReplyResult debugReply(@Valid @RequestBody CustomerMessage message) {
        return knowledgeAnswerService.answer(message);
    }
}

package com.example.customerservice.model.knowledge;

public record FeishuKnowledgeDocument(
        String token,
        String type,
        String title,
        String sourceReference,
        String text
) {
}

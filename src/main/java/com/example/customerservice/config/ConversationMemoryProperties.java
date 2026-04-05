package com.example.customerservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.memory")
public record ConversationMemoryProperties(
        boolean enabled,
        int maxTurns,
        int maxCharsPerMessage
) {
}

package com.example.customerservice.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.rag")
public record RagProperties(
        boolean enabled,
        @Min(1) int topK,
        double similarityThreshold,
        @Min(1) int maxCharsPerDoc
) {
}

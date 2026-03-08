package com.example.customerservice.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.ai")
public record AiProviderProperties(
        @NotBlank String provider,
        @NotBlank String systemPrompt
) {
}

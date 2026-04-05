package com.example.customerservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.feishu")
public record FeishuProperties(
        String baseUrl,
        String verificationToken,
        String encryptKey,
        Bot bot
) {
    public record Bot(
            String appId,
            String appSecret
    ) {
    }
}

package com.example.customerservice.service;

import com.example.customerservice.config.FeishuProperties;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class FeishuAuthService {

    private static final long EXPIRE_BUFFER_SECONDS = 60;

    private final FeishuProperties feishuProperties;
    private final RestClient restClient;

    private volatile String cachedTenantAccessToken;
    private volatile Instant cachedExpiresAt = Instant.EPOCH;

    public FeishuAuthService(FeishuProperties feishuProperties, RestClient.Builder restClientBuilder) {
        this.feishuProperties = feishuProperties;
        this.restClient = restClientBuilder
                .baseUrl(normalizeBaseUrl(feishuProperties.baseUrl()))
                .build();
    }

    public String getTenantAccessToken() {
        if (cachedTenantAccessToken != null && Instant.now().isBefore(cachedExpiresAt)) {
            return cachedTenantAccessToken;
        }

        synchronized (this) {
            if (cachedTenantAccessToken != null && Instant.now().isBefore(cachedExpiresAt)) {
                return cachedTenantAccessToken;
            }

            String appId = Objects.requireNonNull(feishuProperties.bot(), "app.feishu.bot must be configured").appId();
            String appSecret = Objects.requireNonNull(feishuProperties.bot(), "app.feishu.bot must be configured").appSecret();
            if (appId == null || appId.isBlank() || appSecret == null || appSecret.isBlank()) {
                throw new IllegalStateException("Feishu bot app-id/app-secret are not configured.");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("/auth/v3/tenant_access_token/internal")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "app_id", appId,
                            "app_secret", appSecret
                    ))
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new IllegalStateException("Empty response while requesting Feishu tenant_access_token.");
            }

            Object code = response.get("code");
            if (!(code instanceof Number codeNumber) || codeNumber.intValue() != 0) {
                throw new IllegalStateException("Failed to get Feishu tenant_access_token: " + response);
            }

            String token = String.valueOf(response.get("tenant_access_token"));
            Number expire = (Number) response.getOrDefault("expire", 7200);
            cachedTenantAccessToken = token;
            cachedExpiresAt = Instant.now().plusSeconds(Math.max(expire.longValue() - EXPIRE_BUFFER_SECONDS, 60));
            return token;
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        String value = Objects.toString(baseUrl, "").trim();
        if (value.isEmpty()) {
            return "https://open.feishu.cn/open-apis";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}

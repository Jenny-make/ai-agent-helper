package com.example.customerservice.service;

import com.example.customerservice.config.FeishuProperties;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class FeishuMessageService {

    private final RestClient restClient;
    private final FeishuAuthService feishuAuthService;

    public FeishuMessageService(FeishuProperties feishuProperties,
                                FeishuAuthService feishuAuthService,
                                RestClient.Builder restClientBuilder) {
        this.feishuAuthService = feishuAuthService;
        this.restClient = restClientBuilder
                .baseUrl(normalizeBaseUrl(feishuProperties.baseUrl()))
                .build();
    }

    public void replyText(String messageId, String answer) {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("Feishu messageId must not be blank.");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri("/im/v1/messages/{message_id}/reply", messageId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + feishuAuthService.getTenantAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "msg_type", "text",
                        "content", "{\"text\":" + quoteJson(answer) + "}"
                ))
                .retrieve()
                .body(Map.class);

        if (response == null) {
            throw new IllegalStateException("Empty response while replying to Feishu message.");
        }

        Object code = response.get("code");
        if (!(code instanceof Number codeNumber) || codeNumber.intValue() != 0) {
            throw new IllegalStateException("Failed to reply Feishu message: " + response);
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        String value = Objects.toString(baseUrl, "").trim();
        if (value.isEmpty()) {
            return "https://open.feishu.cn/open-apis";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String quoteJson(String text) {
        String value = Objects.toString(text, "");
        String escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
        return "\"" + escaped + "\"";
    }
}

package com.example.customerservice.service;

import com.example.customerservice.config.FeishuProperties;
import com.example.customerservice.model.knowledge.FeishuKnowledgeDocument;
import com.example.customerservice.model.knowledge.FeishuKnowledgeSource;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class FeishuOpenApiKnowledgeSourceClient implements FeishuKnowledgeSourceClient {

    private final RestClient restClient;
    private final FeishuAuthService feishuAuthService;

    public FeishuOpenApiKnowledgeSourceClient(
            FeishuProperties feishuProperties,
            FeishuAuthService feishuAuthService,
            RestClient.Builder restClientBuilder
    ) {
        this.feishuAuthService = feishuAuthService;
        this.restClient = restClientBuilder
                .baseUrl(normalizeBaseUrl(feishuProperties.baseUrl()))
                .build();
    }

    @Override
    public FeishuKnowledgeDocument fetch(FeishuKnowledgeSource source) {
        String type = source.resolvedType();
        if (!"docx".equals(type)) {
            throw new IllegalArgumentException(
                    "Unsupported Feishu knowledge source type '" + source.resolvedType()
                            + "'. First-pass sync currently supports only 'docx'."
            );
        }
        String token = source.resolvedToken();
        if (token.isBlank()) {
            throw new IllegalArgumentException("Feishu knowledge source token is blank and could not be inferred from sourceUrl.");
        }

        JsonNode response = restClient.get()
                .uri("/docx/v1/documents/{document_id}/raw_content", token)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + feishuAuthService.getTenantAccessToken())
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new IllegalStateException("Empty response while reading Feishu docx raw content.");
        }

        int code = response.path("code").asInt(-1);
        if (code != 0) {
            throw new IllegalStateException("Failed to read Feishu docx raw content: " + response);
        }

        JsonNode data = response.path("data");
        String rawContent = firstNonBlank(
                data.path("content").asText(""),
                data.path("raw_content").asText("")
        );
        if (rawContent.isBlank()) {
            throw new IllegalStateException(
                    "Feishu docx raw content is empty for token '" + token + "'."
            );
        }

        String title = firstNonBlank(
                source.normalizedTitle(),
                data.path("title").asText(""),
                data.path("document").path("title").asText(""),
                token
        );

        return new FeishuKnowledgeDocument(
                token,
                type,
                title,
                buildSourceReference(source),
                normalizeContent(rawContent)
        );
    }

    private String buildSourceReference(FeishuKnowledgeSource source) {
        String sourceUrl = source.normalizedSourceUrl();
        if (!sourceUrl.isBlank()) {
            return sourceUrl;
        }
        return "feishu-docx:" + source.normalizedToken();
    }

    private String normalizeBaseUrl(String baseUrl) {
        String value = Objects.toString(baseUrl, "").trim();
        if (value.isEmpty()) {
            return "https://open.feishu.cn/open-apis";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String normalizeContent(String text) {
        return Objects.toString(text, "")
                .replace('\u00A0', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll("[ ]{2,}", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            String value = Objects.toString(candidate, "").trim();
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}

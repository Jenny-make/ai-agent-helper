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
        if ("wiki".equals(type)) {
            return fetchWikiBackedDocument(source);
        }
        if (!"docx".equals(type)) {
            throw new IllegalArgumentException(
                    "Unsupported Feishu knowledge source type '" + source.resolvedType()
                            + "'. First-pass sync currently supports 'docx' and wiki pages backed by docx."
            );
        }
        String token = source.resolvedToken();
        if (token.isBlank()) {
            throw new IllegalArgumentException("Feishu knowledge source token is blank and could not be inferred from sourceUrl.");
        }

        return fetchDocxDocument(source, token, type, buildSourceReference(source));
    }

    private FeishuKnowledgeDocument fetchWikiBackedDocument(FeishuKnowledgeSource source) {
        String wikiToken = source.resolvedToken();
        if (wikiToken.isBlank()) {
            throw new IllegalArgumentException("Feishu wiki source token is blank and could not be inferred from sourceUrl.");
        }

        JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/wiki/v2/spaces/get_node")
                        .queryParam("token", wikiToken)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + feishuAuthService.getTenantAccessToken())
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new IllegalStateException("Empty response while reading Feishu wiki node.");
        }

        int code = response.path("code").asInt(-1);
        if (code != 0) {
            throw new IllegalStateException("Failed to read Feishu wiki node: " + response);
        }

        JsonNode data = response.path("data");
        JsonNode node = data.has("node") ? data.path("node") : data;
        String objType = firstNonBlank(node.path("obj_type").asText(""), node.path("objType").asText(""));
        String objToken = firstNonBlank(node.path("obj_token").asText(""), node.path("objToken").asText(""));
        if (!"docx".equals(objType)) {
            throw new IllegalArgumentException(
                    "Unsupported Feishu wiki node obj_type '" + objType + "'. Current sync supports wiki nodes backed by docx only."
            );
        }
        if (objToken.isBlank()) {
            throw new IllegalStateException("Feishu wiki node did not include obj_token for token '" + wikiToken + "'.");
        }

        FeishuKnowledgeSource docxSource = new FeishuKnowledgeSource(
                objToken,
                "docx",
                firstNonBlank(source.normalizedTitle(), node.path("title").asText(""), wikiToken),
                source.normalizedSourceUrl()
        );
        return fetchDocxDocument(docxSource, objToken, "wiki", buildSourceReference(source));
    }

    private FeishuKnowledgeDocument fetchDocxDocument(
            FeishuKnowledgeSource source,
            String token,
            String documentType,
            String sourceReference
    ) {
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
                documentType,
                title,
                sourceReference,
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

package com.example.customerservice.service;

import com.example.customerservice.config.FeishuProperties;
import com.example.customerservice.model.knowledge.FeishuKnowledgeSource;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class FeishuDriveKnowledgeSourceDiscoveryService implements FeishuKnowledgeSourceDiscoveryService {

    private static final Logger logger = LoggerFactory.getLogger(FeishuDriveKnowledgeSourceDiscoveryService.class);

    private final RestClient restClient;
    private final FeishuAuthService feishuAuthService;

    public FeishuDriveKnowledgeSourceDiscoveryService(
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
    public List<FeishuKnowledgeSource> expand(List<FeishuKnowledgeSource> sources) {
        Map<String, FeishuKnowledgeSource> expanded = new LinkedHashMap<>();
        List<FeishuKnowledgeSource> safeSources = sources == null ? List.of() : sources;
        for (FeishuKnowledgeSource source : safeSources) {
            if (source == null || source.resolvedToken().isBlank()) {
                continue;
            }
            if ("folder".equals(source.resolvedType())) {
                collectFolderDocuments(source, expanded);
                continue;
            }
            expanded.putIfAbsent(source.sourceKey(), source);
        }
        return new ArrayList<>(expanded.values());
    }

    private void collectFolderDocuments(FeishuKnowledgeSource folderSource, Map<String, FeishuKnowledgeSource> target) {
        String pageToken = "";
        do {
            String currentPageToken = pageToken;
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/drive/v1/files")
                            .queryParam("folder_token", folderSource.resolvedToken())
                            .queryParam("page_size", 200)
                            .queryParamIfPresent(
                                    "page_token",
                                    currentPageToken.isBlank()
                                            ? java.util.Optional.empty()
                                            : java.util.Optional.of(currentPageToken)
                            )
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + feishuAuthService.getTenantAccessToken())
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                throw new IllegalStateException("Empty response while listing Feishu folder files.");
            }
            int code = response.path("code").asInt(-1);
            if (code != 0) {
                throw new IllegalStateException("Failed to list Feishu folder files: " + response);
            }

            JsonNode data = response.path("data");
            for (JsonNode file : data.path("files")) {
                visitFileNode(file, folderSource, target);
            }

            pageToken = Objects.toString(data.path("next_page_token").asText(""), "").trim();
        } while (!pageToken.isBlank());
    }

    private void visitFileNode(
            JsonNode file,
            FeishuKnowledgeSource parentFolder,
            Map<String, FeishuKnowledgeSource> target
    ) {
        String type = Objects.toString(file.path("type").asText(""), "").trim().toLowerCase();
        String token = Objects.toString(file.path("token").asText(""), "").trim();
        String name = firstNonBlank(
                file.path("name").asText(""),
                file.path("title").asText(""),
                token
        );
        String url = firstNonBlank(
                file.path("url").asText(""),
                file.path("alternate_url").asText(""),
                buildDefaultUrl(type, token)
        );

        switch (type) {
            case "docx" -> {
                FeishuKnowledgeSource source = new FeishuKnowledgeSource(token, "docx", name, url);
                target.putIfAbsent(source.sourceKey(), source);
            }
            case "shortcut" -> collectShortcut(file, target);
            case "folder" -> {
                FeishuKnowledgeSource nestedFolder = new FeishuKnowledgeSource(token, "folder", name, url);
                collectFolderDocuments(nestedFolder, target);
            }
            default -> logger.debug("Skipping unsupported Feishu drive node type={} token={}", type, token);
        }
    }

    private void collectShortcut(JsonNode file, Map<String, FeishuKnowledgeSource> target) {
        JsonNode shortcutInfo = file.path("shortcut_info");
        String targetType = Objects.toString(shortcutInfo.path("target_type").asText(""), "").trim().toLowerCase();
        String targetToken = Objects.toString(shortcutInfo.path("target_token").asText(""), "").trim();
        if (targetToken.isBlank()) {
            return;
        }
        if ("docx".equals(targetType)) {
            FeishuKnowledgeSource source = new FeishuKnowledgeSource(
                    targetToken,
                    "docx",
                    firstNonBlank(file.path("name").asText(""), file.path("title").asText(""), targetToken),
                    buildDefaultUrl("docx", targetToken)
            );
            target.putIfAbsent(source.sourceKey(), source);
            return;
        }
        if ("folder".equals(targetType)) {
            FeishuKnowledgeSource nestedFolder = new FeishuKnowledgeSource(
                    targetToken,
                    "folder",
                    firstNonBlank(file.path("name").asText(""), file.path("title").asText(""), targetToken),
                    buildDefaultUrl("folder", targetToken)
            );
            collectFolderDocuments(nestedFolder, target);
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        String value = Objects.toString(baseUrl, "").trim();
        if (value.isEmpty()) {
            return "https://open.feishu.cn/open-apis";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String buildDefaultUrl(String type, String token) {
        if ("docx".equals(type)) {
            return "https://feishu.cn/docx/" + token;
        }
        if ("folder".equals(type)) {
            return "https://feishu.cn/drive/folder/" + token;
        }
        return "";
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

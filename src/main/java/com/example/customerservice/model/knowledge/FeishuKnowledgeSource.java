package com.example.customerservice.model.knowledge;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record FeishuKnowledgeSource(
        String token,
        String type,
        String title,
        String sourceUrl
) {
    private static final Pattern DOCX_URL_PATTERN = Pattern.compile("/docx/([a-zA-Z0-9]+)");
    private static final Pattern DOC_URL_PATTERN = Pattern.compile("/docs?/([a-zA-Z0-9]+)");
    private static final Pattern FOLDER_URL_PATTERN = Pattern.compile("/folder/([a-zA-Z0-9]+)");

    public String normalizedToken() {
        return Objects.toString(token, "").trim();
    }

    public String normalizedType() {
        String value = Objects.toString(type, "docx").trim().toLowerCase(Locale.ROOT);
        return value.isEmpty() ? "docx" : value;
    }

    public String normalizedTitle() {
        return Objects.toString(title, "").trim();
    }

    public String normalizedSourceUrl() {
        return Objects.toString(sourceUrl, "").trim();
    }

    public String resolvedType() {
        String explicit = normalizedType();
        if (!explicit.isBlank() && !"auto".equals(explicit)) {
            return explicit;
        }

        String path = normalizedPath();
        if (DOCX_URL_PATTERN.matcher(path).find()) {
            return "docx";
        }
        if (FOLDER_URL_PATTERN.matcher(path).find()) {
            return "folder";
        }
        if (DOC_URL_PATTERN.matcher(path).find()) {
            return "doc";
        }
        return explicit.isBlank() ? "docx" : explicit;
    }

    public String resolvedToken() {
        String explicit = normalizedToken();
        if (!explicit.isBlank()) {
            return explicit;
        }

        String path = normalizedPath();
        String matched = matchFirst(path, DOCX_URL_PATTERN);
        if (!matched.isBlank()) {
            return matched;
        }
        matched = matchFirst(path, FOLDER_URL_PATTERN);
        if (!matched.isBlank()) {
            return matched;
        }
        matched = matchFirst(path, DOC_URL_PATTERN);
        if (!matched.isBlank()) {
            return matched;
        }
        return "";
    }

    public String sourceKey() {
        return resolvedType() + ":" + resolvedToken();
    }

    private String normalizedPath() {
        String url = normalizedSourceUrl();
        if (url.isBlank()) {
            return "";
        }
        try {
            return Objects.toString(URI.create(url).getPath(), "");
        } catch (IllegalArgumentException e) {
            return url;
        }
    }

    private String matchFirst(String path, Pattern pattern) {
        Matcher matcher = pattern.matcher(Objects.toString(path, ""));
        if (matcher.find()) {
            return Objects.toString(matcher.group(1), "").trim();
        }
        return "";
    }
}

package com.example.customerservice.config;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * Excludes Spring AI auto-configurations whose API keys are not configured.
 * <p>
 * This prevents startup from failing when multiple model starters are on the classpath,
 * but the user only configures one provider (e.g. MiniMax only).
 */
public class AiAutoconfigureExcluder implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "appAiAutoconfigureExcluder";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String configuredProvider = environment.getProperty("app.ai.provider", "auto")
                .trim()
                .toLowerCase(Locale.ROOT);

        String deepSeekKey = sanitizeApiKey(environment.getProperty("spring.ai.deepseek.api-key", ""));
        String miniMaxKey = sanitizeApiKey(environment.getProperty("spring.ai.minimax.api-key", ""));
        String openAiKey = sanitizeApiKey(environment.getProperty("spring.ai.openai.api-key", ""));

        boolean ragEnabled = Boolean.parseBoolean(environment.getProperty("app.rag.enabled", "false"));

        String provider = inferProvider(configuredProvider, deepSeekKey, miniMaxKey, openAiKey);

        Set<String> excludes = new LinkedHashSet<>();

        // Vector store: only enable when RAG is explicitly enabled.
        if (!ragEnabled) {
            excludes.add("org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusVectorStoreAutoConfiguration");
        }

        // Exclude model auto-configurations that would fail due to missing keys.
        if (!StringUtils.hasText(deepSeekKey)) {
            excludes.add("org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration");
        }
        if (!StringUtils.hasText(openAiKey)) {
            excludes.add("org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration");
            excludes.add("org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration");
            excludes.add("org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration");
            excludes.add("org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration");
            excludes.add("org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration");
            excludes.add("org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration");
        }
        if (!StringUtils.hasText(miniMaxKey)) {
            excludes.add("org.springframework.ai.model.minimax.autoconfigure.MiniMaxChatAutoConfiguration");
            excludes.add("org.springframework.ai.model.minimax.autoconfigure.MiniMaxEmbeddingAutoConfiguration");
        }

        // If user explicitly selects a provider, do not exclude its auto-config (even if key missing).
        // The app will still fail later with a clearer error on first use.
        switch (provider) {
            case "deepseek" -> excludes.remove("org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration");
            case "minimax" -> {
                excludes.remove("org.springframework.ai.model.minimax.autoconfigure.MiniMaxChatAutoConfiguration");
                excludes.remove("org.springframework.ai.model.minimax.autoconfigure.MiniMaxEmbeddingAutoConfiguration");
            }
            case "openai", "qwen" -> excludes.removeIf(v -> v.startsWith("org.springframework.ai.model.openai.autoconfigure."));
            default -> { }
        }

        if (excludes.isEmpty()) {
            return;
        }

        Set<String> merged = new LinkedHashSet<>(readExistingExcludes(environment));
        merged.addAll(excludes);

        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("spring.autoconfigure.exclude", merged.stream().collect(Collectors.joining(",")));
        if ("auto".equals(configuredProvider) && !"auto".equals(provider)) {
            map.put("app.ai.provider", provider);
        }
        // Override sanitized API keys if needed (common when users paste keys with angle brackets).
        if (StringUtils.hasText(miniMaxKey)) {
            map.put("spring.ai.minimax.api-key", miniMaxKey);
        }
        if (StringUtils.hasText(deepSeekKey)) {
            map.put("spring.ai.deepseek.api-key", deepSeekKey);
        }
        if (StringUtils.hasText(openAiKey)) {
            map.put("spring.ai.openai.api-key", openAiKey);
        }

        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, map));

        if (Boolean.parseBoolean(environment.getProperty("APP_AI_DEBUG_EXCLUDER", "false"))) {
            System.err.println("[AiAutoconfigureExcluder] configuredProvider=" + configuredProvider
                    + " inferredProvider=" + provider
                    + " ragEnabled=" + ragEnabled
                    + " exclude=" + map.get("spring.autoconfigure.exclude"));
        }
    }

    private static String inferProvider(String configuredProvider, String deepSeekKey, String miniMaxKey, String openAiKey) {
        if (!"auto".equals(configuredProvider)) {
            return configuredProvider;
        }

        if (StringUtils.hasText(miniMaxKey)) {
            return "minimax";
        }
        if (StringUtils.hasText(deepSeekKey)) {
            return "deepseek";
        }
        if (StringUtils.hasText(openAiKey)) {
            return "openai";
        }
        return "auto";
    }

    private static String sanitizeApiKey(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("<") && trimmed.endsWith(">")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private static Set<String> readExistingExcludes(ConfigurableEnvironment environment) {
        Set<String> merged = new LinkedHashSet<>();

        Object existingRaw = environment.getProperty("spring.autoconfigure.exclude");
        if (existingRaw instanceof String s && StringUtils.hasText(s)) {
            for (String item : s.split(",")) {
                if (StringUtils.hasText(item)) {
                    merged.add(item.trim());
                }
            }
            return merged;
        }

        List<String> existingList = environment.getProperty("spring.autoconfigure.exclude", List.class, List.of());
        if (existingList != null) {
            merged.addAll(existingList);
        }
        return merged;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

package com.example.customerservice.service;

import com.example.customerservice.config.FeishuKnowledgeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class FeishuKnowledgeStartupSyncRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(FeishuKnowledgeStartupSyncRunner.class);

    private final FeishuKnowledgeProperties properties;
    private final FeishuKnowledgeSyncService feishuKnowledgeSyncService;

    public FeishuKnowledgeStartupSyncRunner(
            FeishuKnowledgeProperties properties,
            FeishuKnowledgeSyncService feishuKnowledgeSyncService
    ) {
        this.properties = properties;
        this.feishuKnowledgeSyncService = feishuKnowledgeSyncService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled() || !properties.autoSyncOnStartup()) {
            return;
        }

        try {
            var result = feishuKnowledgeSyncService.syncConfiguredSources();
            logger.info(
                    "feishu.knowledge.startupSync success={} requestedSources={} syncedSources={} syncedChunks={}",
                    result.success(),
                    result.requestedSources(),
                    result.syncedSources(),
                    result.syncedChunks()
            );
        } catch (Exception e) {
            logger.warn("feishu.knowledge.startupSync failed: {}", e.getMessage(), e);
        }
    }
}

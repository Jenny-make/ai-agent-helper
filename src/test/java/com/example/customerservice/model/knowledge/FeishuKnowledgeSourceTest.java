package com.example.customerservice.model.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FeishuKnowledgeSourceTest {

    @Test
    void resolvesWikiLinkTokenAndType() {
        FeishuKnowledgeSource source = new FeishuKnowledgeSource(
                "",
                "auto",
                "",
                "https://example.feishu.cn/wiki/U807wYNyJWoarkHAZcwNbOne?fromScene=spaceOverview"
        );

        assertEquals("wiki", source.resolvedType());
        assertEquals("U807wYNyJWoarkHAZcwNbOne", source.resolvedToken());
        assertEquals("wiki:U807wYNyJWoarkHAZcwNbOne", source.sourceKey());
    }
}

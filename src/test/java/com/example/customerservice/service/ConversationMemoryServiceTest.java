package com.example.customerservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.customerservice.config.ConversationMemoryProperties;
import com.example.customerservice.model.SessionContextWindow;
import com.example.customerservice.model.SessionRole;
import org.junit.jupiter.api.Test;

class ConversationMemoryServiceTest {

    @Test
    void contextWindowUsesSummaryPlusRecentVerbatimTurns() {
        ConversationMemoryService service =
                new ConversationMemoryService(new ConversationMemoryProperties(true, 6, 1200, 2, 400));

        service.appendExchange("session-1", "question 1 about refunds", "answer 1");
        service.appendExchange("session-1", "question 2 about refund status", "answer 2");
        service.appendExchange("session-1", "question 3 about shipping", "answer 3");
        service.appendExchange("session-1", "question 4 about delivery time", "answer 4");

        SessionContextWindow contextWindow = service.getContextWindow("session-1");

        assertEquals(8, contextWindow.recentMessages().size());
        assertEquals(SessionRole.USER, contextWindow.recentMessages().get(0).role());
        assertTrue(contextWindow.summary().isPresent());
        assertTrue(contextWindow.summary().orElseThrow().summary().contains("question 1"));
        assertEquals(2, contextWindow.promptTurns().size());
        assertEquals("question 3 about shipping", contextWindow.promptTurns().get(0).userMessage());
        assertEquals("question 4 about delivery time", contextWindow.latestTurn().orElseThrow().userMessage());
    }

    @Test
    void rolledSummaryPreservesTurnsEvictedFromRecentWindow() {
        ConversationMemoryService service =
                new ConversationMemoryService(new ConversationMemoryProperties(true, 3, 1200, 2, 400));

        service.appendExchange("session-2", "question 1 about damaged order", "answer 1");
        service.appendExchange("session-2", "question 2 about compensation", "answer 2");
        service.appendExchange("session-2", "question 3 about shipping", "answer 3");
        service.appendExchange("session-2", "question 4 about escalation", "answer 4");

        SessionContextWindow contextWindow = service.getContextWindow("session-2");

        assertEquals(3, contextWindow.recentTurns().size());
        assertEquals(2, contextWindow.promptTurns().size());
        assertTrue(contextWindow.summary().isPresent());
        assertTrue(contextWindow.summary().orElseThrow().summary().contains("question 1 about damaged order"));
    }
}

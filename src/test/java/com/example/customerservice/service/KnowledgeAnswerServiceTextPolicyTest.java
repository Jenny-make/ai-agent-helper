package com.example.customerservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.example.customerservice.config.AiProviderProperties;
import com.example.customerservice.config.ConversationMemoryProperties;
import com.example.customerservice.config.RagProperties;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class KnowledgeAnswerServiceTextPolicyTest {

    private KnowledgeAnswerService knowledgeAnswerService;

    @BeforeEach
    void setUp() {
        knowledgeAnswerService = new KnowledgeAnswerService(
                new AiProviderProperties("auto", "You are a helpful assistant."),
                new RagProperties(false, 4, 0.0, 1200),
                mock(ObjectProvider.class),
                mock(ObjectProvider.class),
                mock(ObjectProvider.class),
                mock(ObjectProvider.class),
                new ConversationMemoryService(new ConversationMemoryProperties(true, 6, 1200))
        );
    }

    @Test
    void englishQuestionDoesNotGetForcedToChinese() throws Exception {
        Object language = invoke("determineResponseLanguage", new Class<?>[]{String.class}, "what day is it today");
        assertEquals("AUTO", language.toString());
    }

    @Test
    void standaloneQuestionDoesNotUseConversationContext() throws Exception {
        boolean useConversationContext = (boolean) invoke(
                "shouldUseConversationContext",
                new Class<?>[]{String.class, boolean.class},
                "who is kobe?",
                false
        );

        assertFalse(useConversationContext);
    }

    @Test
    void ambiguousFollowUpStillUsesConversationContext() throws Exception {
        boolean ambiguous = (boolean) invoke(
                "isLikelyAmbiguousFollowUp",
                new Class<?>[]{String.class},
                "what about him?"
        );
        boolean useConversationContext = (boolean) invoke(
                "shouldUseConversationContext",
                new Class<?>[]{String.class, boolean.class},
                "what about him?",
                false
        );

        assertTrue(ambiguous);
        assertTrue(useConversationContext);
    }

    @Test
    void normalItQuestionIsNotTreatedAsAmbiguousFollowUp() throws Exception {
        boolean ambiguous = (boolean) invoke(
                "isLikelyAmbiguousFollowUp",
                new Class<?>[]{String.class},
                "what day is it today"
        );

        assertFalse(ambiguous);
    }

    @Test
    void promptLeakPatternIsDetected() throws Exception {
        boolean promptLeak = (boolean) invoke(
                "looksLikePromptLeak",
                new Class<?>[]{String.class},
                "Current user message:\n<CURRENT_USER_MESSAGE>\nhello\n</CURRENT_USER_MESSAGE>"
        );

        assertTrue(promptLeak);
    }

    private Object invoke(String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = KnowledgeAnswerService.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(knowledgeAnswerService, args);
    }
}

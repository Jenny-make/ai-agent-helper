package com.example.customerservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.example.customerservice.config.AiProviderProperties;
import com.example.customerservice.config.ConversationMemoryProperties;
import com.example.customerservice.config.RagProperties;
import com.example.customerservice.model.ConversationTurn;
import com.example.customerservice.model.SessionContextWindow;
import com.example.customerservice.model.SessionSummary;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
                new ConversationMemoryService(new ConversationMemoryProperties(true, 6, 1200, 4, 1200))
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
                new Class<?>[]{String.class, boolean.class, boolean.class},
                "who is kobe?",
                false,
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
                new Class<?>[]{String.class, boolean.class, boolean.class},
                "what about him?",
                false,
                true
        );

        assertTrue(ambiguous);
        assertTrue(useConversationContext);
    }

    @Test
    void pronounFollowUpIsTreatedAsAmbiguous() throws Exception {
        boolean ambiguous = (boolean) invoke(
                "isLikelyAmbiguousFollowUp",
                new Class<?>[]{String.class},
                "how many champions did he win"
        );

        assertTrue(ambiguous);
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

    @Test
    void metaResponsePatternIsDetected() throws Exception {
        boolean metaResponse = (boolean) invoke(
                "looksLikeMetaResponse",
                new Class<?>[]{String.class},
                "The current user message is asking how many champions did he win. The user is asking for a specific number."
        );

        assertTrue(metaResponse);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void conversationSummaryIsIncludedInPromptAlongsideRecentConversation() throws Exception {
        Class<?> responseLanguageClass = Class.forName(
                "com.example.customerservice.service.KnowledgeAnswerService$ResponseLanguage"
        );
        Object auto = Enum.valueOf((Class<? extends Enum>) responseLanguageClass.asSubclass(Enum.class), "AUTO");

        String prompt = (String) invoke(
                "buildUserPrompt",
                new Class<?>[]{
                        String.class,
                        List.class,
                        String.class,
                        responseLanguageClass,
                        boolean.class,
                        boolean.class,
                        ConversationTurn.class
                },
                "what about the refund timeline?",
                List.of(new ConversationTurn("I want a refund for order 12345", "Please share the order number.")),
                "Topic: refund request\n- User asked: I want a refund for order 12345.",
                auto,
                false,
                true,
                new ConversationTurn("I want a refund for order 12345", "Please share the order number.")
        );

        assertTrue(prompt.contains("<CONVERSATION_SUMMARY>"));
        assertTrue(prompt.contains("<RECENT_CONVERSATION>"));
    }

    @Test
    void retainedConversationContextIsDetected() throws Exception {
        SessionContextWindow contextWindow = new SessionContextWindow(
                "session-1",
                List.of(),
                List.of(new ConversationTurn("who is kobe?", "Kobe Bryant was a basketball player.")),
                List.of(new ConversationTurn("who is kobe?", "Kobe Bryant was a basketball player.")),
                Optional.of(new ConversationTurn("who is kobe?", "Kobe Bryant was a basketball player.")),
                Optional.of(new SessionSummary(
                        "session-1",
                        1,
                        "kobe",
                        "- User asked: who is kobe?",
                        Instant.now()
                ))
        );

        boolean retained = (boolean) invoke(
                "hasRetainedConversationContext",
                new Class<?>[]{SessionContextWindow.class},
                contextWindow
        );

        assertTrue(retained);
    }

    @Test
    void chineseRewriteRequestIsRecognizedAsLanguageSwitchFollowUp() throws Exception {
        boolean languageSwitchFollowUp = (boolean) invoke(
                "isLanguageSwitchFollowUp",
                new Class<?>[]{String.class, Optional.class},
                "\u7528\u4e2d\u6587\u91cd\u65b0\u56de\u7b54\u4e0a\u4e00\u4e2a\u95ee\u9898",
                Optional.of(new ConversationTurn("how many championships did he win?", "He won five championships."))
        );

        assertTrue(languageSwitchFollowUp);
    }

    private Object invoke(String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = KnowledgeAnswerService.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(knowledgeAnswerService, args);
    }
}

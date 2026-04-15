package com.example.customerservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.example.customerservice.config.AiProviderProperties;
import com.example.customerservice.config.ConversationMemoryProperties;
import com.example.customerservice.config.RagProperties;
import com.example.customerservice.model.ActiveDocument;
import com.example.customerservice.model.ConversationTurn;
import com.example.customerservice.model.CustomerMessage;
import com.example.customerservice.model.ReplyResult;
import com.example.customerservice.model.SessionContextWindow;
import com.example.customerservice.model.SessionSummary;
import com.example.customerservice.model.TaskMode;
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
                new ConversationMemoryService(new ConversationMemoryProperties(true, 6, 1200, 4, 1200)),
                new ActiveDocumentService((org.springframework.jdbc.core.JdbcOperations) null),
                new TaskModeClassifier()
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
                new Class<?>[]{String.class, boolean.class, boolean.class, TaskMode.class},
                "who is kobe?",
                false,
                false,
                TaskMode.SMALL_TALK
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
                new Class<?>[]{String.class, boolean.class, boolean.class, TaskMode.class},
                "what about him?",
                false,
                true,
                TaskMode.SMALL_TALK
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
    void translatedPromptLeakPatternIsDetected() throws Exception {
        boolean promptLeak = (boolean) invoke(
                "looksLikePromptLeak",
                new Class<?>[]{String.class},
                "\u5f53\u524d\u7528\u6237\u6d88\u606f\uff1a\n\u8fd9\u7bc7\u6587\u7ae0\u6709\u6ca1\u6709\u4ed6\u4eba\u8bc4\u4ef7\uff1f\n\u9996\u9009\u56de\u590d\u8bed\u8a00\uff1a\u7b80\u4f53\u4e2d\u6587"
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
    void repeatedChineseColonPhraseIsDetectedAsMetaResponse() throws Exception {
        boolean metaResponse = (boolean) invoke(
                "looksLikeMetaResponse",
                new Class<?>[]{String.class},
                "\u8fd9\u7bc7\u6587\u7ae0\u6709\u6ca1\u6709\u4ed6\u4eba\u7684\u8bc4\u4ef7\uff1f"
                        + "\u8fd9\u7bc7\u6587\u7ae0\u7684\u8bc4\u4ef7\u662f\uff1a".repeat(20)
        );

        assertTrue(metaResponse);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void repeatedChineseColonPhraseSanitizesToProcessingFallback() throws Exception {
        Class<?> responseLanguageClass = Class.forName(
                "com.example.customerservice.service.KnowledgeAnswerService$ResponseLanguage"
        );
        Object chinese = Enum.valueOf((Class<? extends Enum>) responseLanguageClass.asSubclass(Enum.class), "CHINESE");

        String answer = (String) invoke(
                "sanitizeModelAnswer",
                new Class<?>[]{String.class, responseLanguageClass, String.class},
                "\u8fd9\u7bc7\u6587\u7ae0\u7684\u8bc4\u4ef7\u662f\uff1a".repeat(20),
                chinese,
                "\u8fd9\u7bc7\u6587\u7ae0\u6709\u6ca1\u6709\u4ed6\u4eba\u8bc4\u4ef7"
        );

        assertEquals(
                "\u62b1\u6b49\uff0c\u6211\u521a\u624d\u6ca1\u6709\u6b63\u786e\u5904\u7406\u8fd9\u6761\u6d88\u606f\u3002\u8bf7\u76f4\u63a5\u91cd\u65b0\u53d1\u4e00\u6b21\u95ee\u9898\uff0c\u6211\u4f1a\u53ea\u56de\u7b54\u7b54\u6848\u3002",
                answer
        );
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
                        ConversationTurn.class,
                        TaskMode.class,
                        Optional.class
                },
                "what about the refund timeline?",
                List.of(new ConversationTurn("I want a refund for order 12345", "Please share the order number.")),
                "Topic: refund request\n- User asked: I want a refund for order 12345.",
                auto,
                false,
                true,
                new ConversationTurn("I want a refund for order 12345", "Please share the order number."),
                TaskMode.SMALL_TALK,
                Optional.empty()
        );

        assertTrue(prompt.contains("<THREAD_SUMMARY>"));
        assertTrue(prompt.contains("<THREAD_CONTEXT>"));
        assertTrue(prompt.contains("<RECENT_CONVERSATION>"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void documentFollowUpPromptIncludesActiveDocumentAndThreadContext() throws Exception {
        Class<?> responseLanguageClass = Class.forName(
                "com.example.customerservice.service.KnowledgeAnswerService$ResponseLanguage"
        );
        Object chinese = Enum.valueOf((Class<? extends Enum>) responseLanguageClass.asSubclass(Enum.class), "CHINESE");
        ActiveDocument activeDocument = new ActiveDocument(
                "session-doc",
                "doc-token",
                "测试论文",
                "feishu-docx:doc-token",
                "doc-token",
                Instant.now()
        );

        String prompt = (String) invoke(
                "buildUserPrompt",
                new Class<?>[]{
                        String.class,
                        List.class,
                        String.class,
                        responseLanguageClass,
                        boolean.class,
                        boolean.class,
                        ConversationTurn.class,
                        TaskMode.class,
                        Optional.class
                },
                "第二个作者是谁？",
                List.of(new ConversationTurn("测试论文的作者是谁？", "作者是张三、李四。")),
                "",
                chinese,
                false,
                false,
                new ConversationTurn("测试论文的作者是谁？", "作者是张三、李四。"),
                TaskMode.FOLLOW_UP_ON_DOCUMENT,
                Optional.of(activeDocument)
        );

        assertTrue(prompt.contains("<TASK_MODE>"));
        assertTrue(prompt.contains("FOLLOW_UP_ON_DOCUMENT"));
        assertTrue(prompt.contains("<ACTIVE_DOCUMENT>"));
        assertTrue(prompt.contains("sourceToken=doc-token"));
        assertTrue(prompt.contains("<THREAD_CONTEXT>"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void documentFollowUpBuildsSourceTokenFilter() throws Exception {
        ActiveDocument activeDocument = new ActiveDocument(
                "session-doc",
                "doc-token",
                "测试论文",
                "feishu-docx:doc-token",
                "doc-token",
                Instant.now()
        );

        Optional<String> filter = (Optional<String>) invoke(
                "buildActiveDocumentFilter",
                new Class<?>[]{Optional.class, TaskMode.class},
                Optional.of(activeDocument),
                TaskMode.FOLLOW_UP_ON_DOCUMENT
        );

        assertTrue(filter.isPresent());
        assertEquals("sourceToken == 'doc-token'", filter.orElseThrow());
    }

    @Test
    void chineseDocumentFollowUpIsClassifiedWhenActiveDocumentExists() {
        ActiveDocument activeDocument = new ActiveDocument(
                "session-doc",
                "doc-token",
                "\u5bd2\u51b7\u4e2d\u7684\u6e29\u6696",
                "feishu-docx:doc-token",
                "doc-token",
                Instant.now()
        );

        TaskMode taskMode = new TaskModeClassifier().classify(
                "\u8fd9\u7bc7\u6587\u7ae0\u6709\u6ca1\u6709\u4ed6\u4eba\u8bc4\u4ef7",
                Optional.of(activeDocument)
        );

        assertEquals(TaskMode.FOLLOW_UP_ON_DOCUMENT, taskMode);
    }

    @Test
    void chineseBookTitleIsRecognizedAsExplicitDocumentTitle() throws Exception {
        boolean explicitTitle = (boolean) invoke(
                "hasExplicitDocumentTitle",
                new Class<?>[]{String.class},
                "\u300a\u5bd2\u51b7\u4e2d\u7684\u6e29\u6696\u300b\u4f5c\u8005\u662f\u8c01"
        );

        assertTrue(explicitTitle);
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

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void simpleIdentifierLookupCanAnswerFromConversationMemory() throws Exception {
        Class<?> responseLanguageClass = Class.forName(
                "com.example.customerservice.service.KnowledgeAnswerService$ResponseLanguage"
        );
        Object chinese = Enum.valueOf((Class<? extends Enum>) responseLanguageClass.asSubclass(Enum.class), "CHINESE");
        SessionContextWindow contextWindow = new SessionContextWindow(
                "session-identifier",
                List.of(),
                List.of(new ConversationTurn(
                        "\u6211\u7684\u4e34\u65f6\u8ba2\u5355\u53f7\u662f ORDER-0415-A7\u3002\u8bf7\u56de\u590d\u201c\u5df2\u8bb0\u5f55\u201d\u3002",
                        "\u60a8\u7684\u4e34\u65f6\u8ba2\u5355\u53f7\u662f ORDER-0415-A7\u3002\u5df2\u8bb0\u5f55\u3002"
                )),
                List.of(new ConversationTurn(
                        "\u6211\u7684\u4e34\u65f6\u8ba2\u5355\u53f7\u662f ORDER-0415-A7\u3002\u8bf7\u56de\u590d\u201c\u5df2\u8bb0\u5f55\u201d\u3002",
                        "\u60a8\u7684\u4e34\u65f6\u8ba2\u5355\u53f7\u662f ORDER-0415-A7\u3002\u5df2\u8bb0\u5f55\u3002"
                )),
                Optional.of(new ConversationTurn(
                        "\u6211\u7684\u4e34\u65f6\u8ba2\u5355\u53f7\u662f ORDER-0415-A7\u3002\u8bf7\u56de\u590d\u201c\u5df2\u8bb0\u5f55\u201d\u3002",
                        "\u60a8\u7684\u4e34\u65f6\u8ba2\u5355\u53f7\u662f ORDER-0415-A7\u3002\u5df2\u8bb0\u5f55\u3002"
                )),
                Optional.empty()
        );

        Optional<String> answer = (Optional<String>) invoke(
                "answerSimpleMemoryLookup",
                new Class<?>[]{String.class, SessionContextWindow.class, responseLanguageClass},
                "\u6211\u7684\u4e34\u65f6\u8ba2\u5355\u53f7\u662f\u4ec0\u4e48\uff1f",
                contextWindow,
                chinese
        );

        assertTrue(answer.isPresent());
        assertEquals("\u4f60\u7684\u4e34\u65f6\u8ba2\u5355\u53f7\u662f ORDER-0415-A7\u3002", answer.orElseThrow());
    }

    @Test
    void processingFallbackIsDetected() throws Exception {
        boolean fallback = (boolean) invoke(
                "isProcessingFallbackAnswer",
                new Class<?>[]{String.class},
                "\u62b1\u6b49\uff0c\u6211\u521a\u624d\u6ca1\u6709\u6b63\u786e\u5904\u7406\u8fd9\u6761\u6d88\u606f\u3002\u8bf7\u76f4\u63a5\u91cd\u65b0\u53d1\u4e00\u6b21\u95ee\u9898\uff0c\u6211\u4f1a\u53ea\u56de\u7b54\u7b54\u6848\u3002"
        );

        assertTrue(fallback);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void documentInsufficientAnswerIsNotProcessingFallback() throws Exception {
        Class<?> responseLanguageClass = Class.forName(
                "com.example.customerservice.service.KnowledgeAnswerService$ResponseLanguage"
        );
        Object chinese = Enum.valueOf((Class<? extends Enum>) responseLanguageClass.asSubclass(Enum.class), "CHINESE");

        String answer = (String) invoke(
                "documentInsufficientAnswer",
                new Class<?>[]{responseLanguageClass, String.class},
                chinese,
                "\u8fd9\u7bc7\u6587\u7ae0\u6709\u6ca1\u6709\u4ed6\u4eba\u8bc4\u4ef7"
        );
        boolean fallback = (boolean) invoke(
                "isProcessingFallbackAnswer",
                new Class<?>[]{String.class},
                answer
        );

        assertEquals("\u6839\u636e\u5f53\u524d\u68c0\u7d22\u5185\u5bb9\uff0c\u6211\u4e0d\u786e\u5b9a\u8fd9\u4e2a\u95ee\u9898\u7684\u7b54\u6848\u3002", answer);
        assertFalse(fallback);
    }

    @Test
    void documentQuestionWithoutRetrievedEvidenceReturnsInsufficientAnswer() {
        ReplyResult result = knowledgeAnswerService.answer(new CustomerMessage(
                "user-doc",
                "session-doc-no-evidence",
                "\u300a\u5bd2\u51b7\u4e2d\u7684\u6e29\u6696\u300b\u4f5c\u8005\u662f\u8c01"
        ));

        assertEquals(
                "\u6839\u636e\u5f53\u524d\u68c0\u7d22\u5185\u5bb9\uff0c\u6211\u4e0d\u786e\u5b9a\u8fd9\u4e2a\u95ee\u9898\u7684\u7b54\u6848\u3002",
                result.answer()
        );
    }

    private Object invoke(String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = KnowledgeAnswerService.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(knowledgeAnswerService, args);
    }
}

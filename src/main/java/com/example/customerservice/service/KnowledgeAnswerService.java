package com.example.customerservice.service;

import com.example.customerservice.config.AiProviderProperties;
import com.example.customerservice.config.RagProperties;
import com.example.customerservice.model.ActiveDocument;
import com.example.customerservice.model.ConversationTurn;
import com.example.customerservice.model.CustomerMessage;
import com.example.customerservice.model.ReplyResult;
import com.example.customerservice.model.SessionContextWindow;
import com.example.customerservice.model.SessionSummary;
import com.example.customerservice.model.TaskMode;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.minimax.MiniMaxChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeAnswerService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeAnswerService.class);
    private static final Pattern EXPLICIT_DOCUMENT_TITLE_PATTERN = Pattern.compile("[《<][^》>]{2,}[》>]");
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile(
            "\\b[A-Za-z]{2,}[A-Za-z0-9]*(?:[-_][A-Za-z0-9]+)+\\b"
    );
    private static final String CHINESE_PROCESSING_FALLBACK =
            "\u62b1\u6b49\uff0c\u6211\u521a\u624d\u6ca1\u6709\u6b63\u786e\u5904\u7406\u8fd9\u6761\u6d88\u606f\u3002\u8bf7\u76f4\u63a5\u91cd\u65b0\u53d1\u4e00\u6b21\u95ee\u9898\uff0c\u6211\u4f1a\u53ea\u56de\u7b54\u7b54\u6848\u3002";
    private static final String ENGLISH_PROCESSING_FALLBACK =
            "Sorry, I didn't process that message correctly. Please send the question again and I'll answer directly.";

    private final AiProviderProperties aiProviderProperties;
    private final RagProperties ragProperties;
    private final ObjectProvider<DeepSeekChatModel> deepSeekChatModelProvider;
    private final ObjectProvider<OpenAiChatModel> openAiChatModelProvider;
    private final ObjectProvider<MiniMaxChatModel> miniMaxChatModelProvider;
    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final ConversationMemoryService conversationMemoryService;
    private final ActiveDocumentService activeDocumentService;
    private final TaskModeClassifier taskModeClassifier;

    public KnowledgeAnswerService(
            AiProviderProperties aiProviderProperties,
            RagProperties ragProperties,
            ObjectProvider<DeepSeekChatModel> deepSeekChatModelProvider,
            ObjectProvider<OpenAiChatModel> openAiChatModelProvider,
            ObjectProvider<MiniMaxChatModel> miniMaxChatModelProvider,
            ObjectProvider<VectorStore> vectorStoreProvider,
            ConversationMemoryService conversationMemoryService,
            ActiveDocumentService activeDocumentService,
            TaskModeClassifier taskModeClassifier
    ) {
        this.aiProviderProperties = aiProviderProperties;
        this.ragProperties = ragProperties;
        this.deepSeekChatModelProvider = deepSeekChatModelProvider;
        this.openAiChatModelProvider = openAiChatModelProvider;
        this.miniMaxChatModelProvider = miniMaxChatModelProvider;
        this.vectorStoreProvider = vectorStoreProvider;
        this.conversationMemoryService = conversationMemoryService;
        this.activeDocumentService = activeDocumentService;
        this.taskModeClassifier = taskModeClassifier;
    }

    public ReplyResult answer(CustomerMessage message) {
        String currentQuestion = Objects.toString(message.text(), "").trim();
        SessionContextWindow contextWindow = conversationMemoryService.getContextWindow(message.sessionId());
        Optional<ActiveDocument> activeDocument = activeDocumentService.getActiveDocument(message.sessionId());
        TaskMode taskMode = taskModeClassifier.classify(currentQuestion, activeDocument);
        Optional<ConversationTurn> latestTurn = contextWindow.latestTurn();
        ResponseLanguage responseLanguage = determineResponseLanguage(currentQuestion);
        boolean languageSwitchFollowUp = isLanguageSwitchFollowUp(currentQuestion, latestTurn);
        boolean ambiguousFollowUp = isLikelyAmbiguousFollowUp(currentQuestion);
        boolean hasRetainedConversationContext = hasRetainedConversationContext(contextWindow);
        boolean useConversationContext = shouldUseConversationContext(
                currentQuestion,
                languageSwitchFollowUp,
                ambiguousFollowUp,
                taskMode
        );
        List<ConversationTurn> promptTurns = useConversationContext ? contextWindow.promptTurns() : List.of();
        String conversationSummary = useConversationContext
                ? contextWindow.summary().map(this::renderConversationSummary).orElse("")
                : "";

        if (ambiguousFollowUp && conversationSummary.isBlank() && promptTurns.isEmpty()) {
            return new ReplyResult(buildClarifyingQuestion(currentQuestion), aiProviderProperties.provider(), List.of());
        }

        Optional<String> directMemoryAnswer = answerSimpleMemoryLookup(
                currentQuestion,
                contextWindow,
                responseLanguage
        );
        if (directMemoryAnswer.isPresent()) {
            String answer = directMemoryAnswer.orElseThrow();
            conversationMemoryService.appendExchange(message.sessionId(), currentQuestion, answer);
            return new ReplyResult(answer, aiProviderProperties.provider(), List.of());
        }

        ChatModel selectedModel = selectChatModel();
        RetrievedKnowledge retrievedKnowledge = retrieveKnowledgeIfEnabled(
                message.sessionId(),
                currentQuestion,
                activeDocument,
                taskMode
        );
        retrievedKnowledge.activeDocument().ifPresent(activeDocumentService::updateActiveDocument);
        Optional<ActiveDocument> effectiveActiveDocument = retrievedKnowledge.activeDocument().or(() -> activeDocument);
        String userPrompt = buildUserPrompt(
                currentQuestion,
                promptTurns,
                conversationSummary,
                responseLanguage,
                languageSwitchFollowUp,
                ambiguousFollowUp,
                latestTurn.orElse(null),
                taskMode,
                effectiveActiveDocument
        );

        logPromptDecision(
                message,
                currentQuestion,
                responseLanguage,
                languageSwitchFollowUp,
                ambiguousFollowUp,
                hasRetainedConversationContext,
                useConversationContext,
                taskMode,
                effectiveActiveDocument,
                contextWindow,
                userPrompt
        );

        ChatClient chatClient = ChatClient.builder(selectedModel).build();
        String rawAnswer = chatClient.prompt()
                .system(buildSystemPrompt(
                        retrievedKnowledge.knowledgeText(),
                        responseLanguage,
                        languageSwitchFollowUp,
                        taskMode,
                        effectiveActiveDocument
                ))
                .user(userPrompt)
                .call()
                .content();
        String answer = sanitizeModelAnswer(rawAnswer, responseLanguage, currentQuestion);
        answer = enforceResponseLanguage(selectedModel, answer, responseLanguage, languageSwitchFollowUp);

        if (logger.isDebugEnabled()) {
            logger.debug(
                    "answer.generated sessionId={} rawAnswerPreview={} sanitizedAnswerPreview={}",
                    message.sessionId(),
                    abbreviate(rawAnswer, 240),
                    abbreviate(answer, 240)
            );
        }

        if (!isProcessingFallbackAnswer(answer)) {
            conversationMemoryService.appendExchange(message.sessionId(), currentQuestion, answer);
        } else if (logger.isDebugEnabled()) {
            logger.debug("answer.memorySkip sessionId={} reason=processingFallback", message.sessionId());
        }
        return new ReplyResult(answer, aiProviderProperties.provider(), retrievedKnowledge.citations());
    }

    public boolean vectorStoreReady() {
        return vectorStoreProvider.getIfAvailable() != null;
    }

    private String buildSystemPrompt(
            String knowledge,
            ResponseLanguage responseLanguage,
            boolean languageSwitchFollowUp,
            TaskMode taskMode,
            Optional<ActiveDocument> activeDocument
    ) {
        boolean hasKnowledge = knowledge != null && !knowledge.isBlank();
        boolean documentGrounded = taskMode == TaskMode.DOCUMENT_QA
                || taskMode == TaskMode.FOLLOW_UP_ON_DOCUMENT
                || taskMode == TaskMode.DEBUG_RAG
                || (activeDocument != null && activeDocument.isPresent());
        String base = Objects.toString(aiProviderProperties.systemPrompt(), "").trim();
        StringBuilder sb = new StringBuilder();
        if (!base.isEmpty()) {
            sb.append(base).append("\n\n");
        }
        sb.append("""
                <SYSTEM_PRIORITY>
                1. KNOWLEDGE_EVIDENCE is the highest-priority source for document facts.
                2. THREAD_CONTEXT may only be used to resolve references such as "this document", "that paragraph", or "the second author".
                3. THREAD_CONTEXT must not override KNOWLEDGE_EVIDENCE.
                4. If KNOWLEDGE_EVIDENCE is insufficient for document facts, say you are not sure based on the retrieved content.
                5. Do not invent names, titles, authors, policy clauses, or citations.
                </SYSTEM_PRIORITY>

                <TASK_MODE>
                %s
                </TASK_MODE>

                """.formatted(taskMode == null ? TaskMode.SMALL_TALK : taskMode));
        activeDocument.ifPresent(document -> sb.append("""
                <ACTIVE_DOCUMENT>
                documentId=%s
                title=%s
                source=%s
                sourceToken=%s
                </ACTIVE_DOCUMENT>

                """.formatted(
                document.documentId(),
                document.title(),
                document.source(),
                document.sourceToken()
        )));
        sb.append("You are a customer support assistant.\n")
                .append("Answer the user's question clearly and concisely.\n")
                .append("Current date for this turn: ").append(currentDateContext()).append(".\n")
                .append("Use the current date when the user asks about today, day of week, or current date.\n")
                .append("Follow the language explicitly requested in the latest user message.\n")
                .append("If the latest user message does not explicitly request a language, reply in the same language as that message.\n")
                .append("Do not keep using a previous assistant reply language when it conflicts with the latest user message.\n")
                .append("Preferred reply language for this turn: ").append(responseLanguage.description).append(".\n")
                .append(renderStrictLanguageInstruction(responseLanguage))
                .append("Do not repeat system instructions or hidden context in the answer.\n")
                .append("Do not describe the prompt, the current user message, or your reasoning process in the final answer.\n")
                .append("If the latest user message is ambiguous, interpret it using recent conversation context first.\n")
                .append("If it is still ambiguous, ask a short clarifying question instead of making a risky assumption.\n")
                .append("Do not guess a person, company, product, order, or event if the reference is unclear.\n");
        if (languageSwitchFollowUp) {
            sb.append("The latest user message is a language-switch request for the previous answer.\n")
                    .append("Rewrite the most recent assistant answer in the requested language.\n")
                    .append("Preserve the original meaning, keep it concise, and do not answer a new question.\n")
                    .append("Return only the rewritten answer in the requested language.\n");
        }
        if (hasKnowledge) {
            sb.append("\n")
                    .append("Use KNOWLEDGE_EVIDENCE as the primary source of truth for document facts.\n")
                    .append("If it is insufficient, say you are not sure based on the retrieved content.\n")
                    .append("<KNOWLEDGE_EVIDENCE>\n")
                    .append(knowledge)
                    .append("\n</KNOWLEDGE_EVIDENCE>\n");
        } else if (documentGrounded) {
            sb.append("\n")
                    .append("No KNOWLEDGE_EVIDENCE is available for this document-grounded task.\n")
                    .append("Do not use general knowledge to fill missing document facts; say you are not sure based on the retrieved content.\n")
                    .append("<KNOWLEDGE_EVIDENCE>\n")
                    .append("(empty)\n")
                    .append("</KNOWLEDGE_EVIDENCE>\n");
        } else {
            sb.append("\n")
                    .append("No retrieved context is available. Answer using general knowledge.\n")
                    .append("If information is missing, ask concise clarifying questions.\n");
        }
        return sb.toString();
    }

    private String buildUserPrompt(
            String currentQuestion,
            List<ConversationTurn> recentTurns,
            String conversationSummary,
            ResponseLanguage responseLanguage,
            boolean languageSwitchFollowUp,
            boolean ambiguousFollowUp,
            ConversationTurn latestTurn,
            TaskMode taskMode,
            Optional<ActiveDocument> activeDocument
    ) {
        String summaryBlock = conversationSummary == null || conversationSummary.isBlank()
                ? ""
                : """
                <THREAD_SUMMARY>
                %s
                </THREAD_SUMMARY>

                """.formatted(conversationSummary);
        String activeDocumentBlock = activeDocument == null || activeDocument.isEmpty()
                ? ""
                : """
                <ACTIVE_DOCUMENT>
                documentId=%s
                title=%s
                source=%s
                sourceToken=%s
                </ACTIVE_DOCUMENT>

                """.formatted(
                activeDocument.orElseThrow().documentId(),
                activeDocument.orElseThrow().title(),
                activeDocument.orElseThrow().source(),
                activeDocument.orElseThrow().sourceToken()
        );
        if (languageSwitchFollowUp && latestTurn != null) {
            return """
                    <TASK_MODE>
                    %s
                    </TASK_MODE>

                    Latest user instruction:
                    <CURRENT_USER_MESSAGE>
                    %s
                    </CURRENT_USER_MESSAGE>

                    Requested reply language:
                    %s

                    Previous user question:
                    <PREVIOUS_USER_MESSAGE>
                    %s
                    </PREVIOUS_USER_MESSAGE>

                    Previous assistant answer:
                    <PREVIOUS_ASSISTANT_ANSWER>
                    %s
                    </PREVIOUS_ASSISTANT_ANSWER>

                    Rewrite the previous assistant answer in the requested reply language.
                    Return only the rewritten answer.
                    Do not keep the original language if it conflicts with the requested reply language.
                    """.formatted(
                    taskMode,
                    currentQuestion,
                    responseLanguage.description,
                    Objects.toString(latestTurn.userMessage(), ""),
                    Objects.toString(latestTurn.assistantMessage(), "")
            );
        }
        if (ambiguousFollowUp && recentTurns != null && !recentTurns.isEmpty()) {
            String conversationTranscript = recentTurns.stream()
                    .map(turn -> "User: " + Objects.toString(turn.userMessage(), "")
                            + "\nAssistant: " + Objects.toString(turn.assistantMessage(), ""))
                    .collect(Collectors.joining("\n\n"));
            return """
                    <TASK_MODE>
                    %s
                    </TASK_MODE>

                    %s%s<THREAD_CONTEXT>
                    <RECENT_CONVERSATION>
                    %s
                    </RECENT_CONVERSATION>
                    </THREAD_CONTEXT>

                    Current user message:
                    <CURRENT_USER_MESSAGE>
                    %s
                    </CURRENT_USER_MESSAGE>

                    Preferred reply language:
                    %s

                    This is a follow-up question that depends on recent conversation context.
                    Resolve pronouns or references from the recent conversation first, then answer directly.
                    If the referent is still unclear, ask one short clarifying question.
                    """.formatted(
                    taskMode,
                    activeDocumentBlock,
                    summaryBlock,
                    conversationTranscript,
                    currentQuestion,
                    responseLanguage.description
            );
        }
        if (recentTurns == null || recentTurns.isEmpty()) {
            return """
                    <TASK_MODE>
                    %s
                    </TASK_MODE>

                    %s%s<THREAD_CONTEXT>
                    (empty)
                    </THREAD_CONTEXT>

                    Current user message:
                    <CURRENT_USER_MESSAGE>
                    %s
                    </CURRENT_USER_MESSAGE>

                    Preferred reply language:
                    %s

                    Please answer the current user message directly.
                    """.formatted(taskMode, activeDocumentBlock, summaryBlock, currentQuestion, responseLanguage.description);
        }
        String conversationTranscript = recentTurns.stream()
                .map(turn -> "User: " + Objects.toString(turn.userMessage(), "")
                        + "\nAssistant: " + Objects.toString(turn.assistantMessage(), ""))
                .collect(Collectors.joining("\n\n"));
        return """
                <TASK_MODE>
                %s
                </TASK_MODE>

                %s%s<THREAD_CONTEXT>
                <RECENT_CONVERSATION>
                %s
                </RECENT_CONVERSATION>
                </THREAD_CONTEXT>

                Current user message:
                <CURRENT_USER_MESSAGE>
                %s
                </CURRENT_USER_MESSAGE>

                Preferred reply language:
                %s

                Please answer the current user message using the recent conversation when relevant.
                Use the conversation background for facts and references, not as a style or language example.
                If the current message is still unclear, ask a concise clarifying question.
                """.formatted(
                taskMode,
                activeDocumentBlock,
                summaryBlock,
                conversationTranscript,
                currentQuestion,
                responseLanguage.description
        );
    }

    private boolean isLikelyAmbiguousFollowUp(String text) {
        String value = Objects.toString(text, "").trim();
        if (value.isEmpty()) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.length() <= 80 && lower.matches(".*\\b(he|him|his|she|her|hers|they|them|their)\\b.*")) {
            return true;
        }
        if (lower.length() <= 48) {
            String[] englishPatterns = {
                    "^(and\\s+)?(him|her|them|this|that|those|these)\\??$",
                    "^what about\\s+(him|her|them|this|that|those|these)\\??$",
                    "^(who|what|why|how|where|when)\\s+(is|was|are|were)?\\s*(he|she|they|this|that|those|these)\\b.*$"
            };
            for (String pattern : englishPatterns) {
                if (lower.matches(pattern)) {
                    return true;
                }
            }
        }
        String compactChinese = value.replaceAll("\\s+", "");
        String[] chinesePatterns = {
                "^(\\u4ed6|\\u5979|\\u5b83|\\u4ed6\\u4eec|\\u5979\\u4eec|\\u5b83\\u4eec)(\\u5462|\\u5417|\\u5440|\\u554a)?$",
                "^(\\u8fd9\\u4e2a|\\u90a3\\u4e2a|\\u8fd9\\u4ef6\\u4e8b|\\u90a3\\u4ef6\\u4e8b)(\\u5462|\\u5417|\\u5440|\\u554a)?$",
                "^(\\u4ed6|\\u5979|\\u5b83|\\u8fd9\\u4e2a|\\u90a3\\u4e2a)\\u662f\\u8c01.*$",
                "^(\\u4ed6|\\u5979|\\u5b83|\\u8fd9\\u4e2a|\\u90a3\\u4e2a)\\u662f\\u4ec0\\u4e48.*$"
        };
        for (String pattern : chinesePatterns) {
            if (compactChinese.matches(pattern)) {
                return true;
            }
        }
        return false;
    }

    private String buildClarifyingQuestion(String text) {
        String value = Objects.toString(text, "");
        if (containsChinese(value)) {
            return "\u6211\u9700\u8981\u4e00\u70b9\u4e0a\u4e0b\u6587\uff0c\u4f60\u6307\u7684\u662f\u8c01\u6216\u54ea\u4ef6\u4e8b\uff1f";
        }
        return "I need a bit more context - who or what are you referring to?";
    }

    private boolean containsChinese(String text) {
        return Objects.toString(text, "").codePoints()
                .anyMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    private int countChineseCharacters(String text) {
        return (int) Objects.toString(text, "").codePoints()
                .filter(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN)
                .count();
    }

    private int countLatinLetters(String text) {
        return (int) Objects.toString(text, "").chars()
                .filter(Character::isLetter)
                .filter(ch -> ch < 128)
                .count();
    }

    private ResponseLanguage determineResponseLanguage(String currentQuestion) {
        ResponseLanguage explicit = detectExplicitLanguagePreference(currentQuestion);
        if (explicit != ResponseLanguage.AUTO) {
            return explicit;
        }
        if (containsChinese(currentQuestion)) {
            return ResponseLanguage.CHINESE;
        }
        return ResponseLanguage.AUTO;
    }

    private ResponseLanguage detectExplicitLanguagePreference(String text) {
        String lower = Objects.toString(text, "").trim().toLowerCase(Locale.ROOT);
        String compact = normalizeLanguageSwitchMessage(text);
        if (containsAny(lower,
                "answer in chinese", "reply in chinese", "respond in chinese", "speak chinese",
                "translate to chinese", "rewrite in chinese")
                || compact.contains("\u4e2d\u6587")
                || compact.contains("\u6c49\u8bed")
                || compact.contains("\u6c49\u5b57")) {
            return ResponseLanguage.CHINESE;
        }
        if (containsAny(lower,
                "answer in english", "reply in english", "respond in english", "speak english",
                "translate to english", "rewrite in english")
                || compact.contains("\u82f1\u6587")
                || compact.contains("\u82f1\u8bed")) {
            return ResponseLanguage.ENGLISH;
        }
        return ResponseLanguage.AUTO;
    }

    private boolean isLanguageSwitchFollowUp(String currentQuestion, Optional<ConversationTurn> latestTurn) {
        if (latestTurn.isEmpty()) {
            return false;
        }
        String normalized = normalizeLanguageSwitchMessage(currentQuestion);
        if (normalized.isEmpty()) {
            return false;
        }
        ResponseLanguage explicit = detectExplicitLanguagePreference(currentQuestion);
        if (explicit == ResponseLanguage.AUTO) {
            return false;
        }
        if (equalsAny(normalized,
                "\u4e2d\u6587", "\u7528\u4e2d\u6587", "\u8bf7\u7528\u4e2d\u6587",
                "\u82f1\u6587", "\u7528\u82f1\u6587", "\u8bf7\u7528\u82f1\u6587",
                "chinese", "answerinchinese", "replyinchinese", "respondinchinese",
                "english", "answerinenglish", "replyinenglish", "respondinenglish")) {
            return true;
        }
        boolean asksToRewrite = containsAny(normalized,
                "\u91cd\u65b0\u56de\u7b54", "\u91cd\u65b0\u56de\u590d", "\u518d\u8bf4\u4e00\u904d", "\u91cd\u65b0\u8bf4\u4e00\u904d",
                "\u7ffb\u8bd1", "\u7ffb\u6210", "\u8bd1\u6210", "\u6539\u6210",
                "rewrite", "translate", "reanswer", "answeragain", "replyagain", "respondagain");
        boolean refersPreviousAnswer = containsAny(normalized,
                "\u4e0a\u4e00\u4e2a\u95ee\u9898", "\u4e0a\u4e00\u4e2a\u56de\u7b54", "\u4e0a\u4e00\u6761", "\u4e0a\u4e00\u6b21",
                "\u521a\u624d", "\u4e0a\u9762", "\u4e4b\u524d",
                "previousanswer", "lastanswer", "previousquestion", "lastquestion", "previousreply", "lastreply", "above");
        return asksToRewrite && refersPreviousAnswer;
    }

    private String normalizeLanguageSwitchMessage(String text) {
        return Objects.toString(text, "")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "")
                .replace("\u3002", "")
                .replace("\uff0c", "")
                .replace("\uff01", "")
                .replace("\uff1f", "")
                .replace(".", "")
                .replace(",", "")
                .replace("!", "")
                .replace("?", "");
    }

    private boolean shouldUseConversationContext(
            String currentQuestion,
            boolean languageSwitchFollowUp,
            boolean ambiguousFollowUp,
            TaskMode taskMode
    ) {
        if (languageSwitchFollowUp || ambiguousFollowUp || taskMode == TaskMode.FOLLOW_UP_ON_DOCUMENT) {
            return true;
        }
        String normalized = normalizeIntentText(currentQuestion);
        return containsAny(normalized,
                "\u7ee7\u7eed", "\u5c55\u5f00\u8bf4\u8bf4", "\u8be6\u7ec6\u70b9", "\u518d\u8bf4\u8bf4", "\u63a5\u7740\u8bf4",
                "continue", "go on", "tell me more", "what about", "and then");
    }

    private String normalizeIntentText(String text) {
        return Objects.toString(text, "")
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private String sanitizeModelAnswer(String answer, ResponseLanguage responseLanguage, String currentQuestion) {
        String value = Objects.toString(answer, "").trim();
        if (!value.isEmpty() && !looksLikePromptLeak(value) && !looksLikeMetaResponse(value)) {
            return value;
        }
        if (responseLanguage == ResponseLanguage.CHINESE || containsChinese(currentQuestion)) {
            return CHINESE_PROCESSING_FALLBACK;
        }
        return ENGLISH_PROCESSING_FALLBACK;
    }

    private Optional<String> answerSimpleMemoryLookup(
            String currentQuestion,
            SessionContextWindow contextWindow,
            ResponseLanguage responseLanguage
    ) {
        if (!isSimpleMemoryLookupQuestion(currentQuestion) || contextWindow == null) {
            return Optional.empty();
        }
        List<ConversationTurn> turns = contextWindow.recentTurns();
        for (int i = turns.size() - 1; i >= 0; i--) {
            ConversationTurn turn = turns.get(i);
            Optional<String> candidate = findIdentifierInText(turn.userMessage())
                    .or(() -> findIdentifierInText(turn.assistantMessage()));
            if (candidate.isPresent()) {
                return Optional.of(formatSimpleMemoryAnswer(
                        currentQuestion,
                        candidate.orElseThrow(),
                        responseLanguage
                ));
            }
        }
        return contextWindow.summary()
                .flatMap(summary -> findIdentifierInText(summary.summary()))
                .map(candidate -> formatSimpleMemoryAnswer(currentQuestion, candidate, responseLanguage));
    }

    private boolean isSimpleMemoryLookupQuestion(String text) {
        String normalized = normalizeIntentText(text);
        return containsAny(normalized,
                "\u8ba2\u5355\u53f7", "\u6d4b\u8bd5\u7801", "\u7f16\u53f7", "\u7f16\u7801",
                "order number", "order id", "test code", "tracking code");
    }

    private Optional<String> findIdentifierInText(String text) {
        String value = Objects.toString(text, "");
        Matcher matcher = IDENTIFIER_PATTERN.matcher(value);
        String latest = "";
        while (matcher.find()) {
            String candidate = matcher.group();
            if (isUsefulIdentifier(candidate)) {
                latest = candidate;
            }
        }
        return latest.isBlank() ? Optional.empty() : Optional.of(latest);
    }

    private boolean isUsefulIdentifier(String candidate) {
        String value = Objects.toString(candidate, "").trim();
        if (value.length() < 5) {
            return false;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        return !containsAny(upper, "CURRENT_USER_MESSAGE", "RECENT_CONVERSATION", "CONVERSATION_SUMMARY");
    }

    private String formatSimpleMemoryAnswer(
            String currentQuestion,
            String identifier,
            ResponseLanguage responseLanguage
    ) {
        boolean chinese = responseLanguage == ResponseLanguage.CHINESE || containsChinese(currentQuestion);
        if (!chinese) {
            return "It is " + identifier + ".";
        }
        if (currentQuestion.contains("\u8ba2\u5355\u53f7")) {
            return "\u4f60\u7684\u4e34\u65f6\u8ba2\u5355\u53f7\u662f " + identifier + "\u3002";
        }
        if (currentQuestion.contains("\u6d4b\u8bd5\u7801")) {
            return "\u521a\u624d\u7684\u6d4b\u8bd5\u7801\u662f " + identifier + "\u3002";
        }
        return "\u662f " + identifier + "\u3002";
    }

    private boolean isProcessingFallbackAnswer(String answer) {
        String value = Objects.toString(answer, "").trim();
        return CHINESE_PROCESSING_FALLBACK.equals(value) || ENGLISH_PROCESSING_FALLBACK.equals(value);
    }

    private String enforceResponseLanguage(
            ChatModel selectedModel,
            String answer,
            ResponseLanguage responseLanguage,
            boolean languageSwitchFollowUp
    ) {
        String value = Objects.toString(answer, "").trim();
        if (value.isEmpty() || !requiresRepair(value, responseLanguage)) {
            return value;
        }
        String repaired = rewriteAnswerInRequiredLanguage(selectedModel, value, responseLanguage, languageSwitchFollowUp);
        if (repaired.isBlank()) {
            return value;
        }
        if (logger.isDebugEnabled()) {
            logger.debug(
                    "answer.languageRepair applied responseLanguage={} originalPreview={} repairedPreview={}",
                    responseLanguage,
                    abbreviate(value, 240),
                    abbreviate(repaired, 240)
            );
        }
        return repaired;
    }

    private boolean requiresRepair(String answer, ResponseLanguage responseLanguage) {
        return switch (responseLanguage) {
            case CHINESE -> !looksChineseEnough(answer);
            case ENGLISH -> !looksEnglishEnough(answer);
            case AUTO -> false;
        };
    }

    private boolean looksChineseEnough(String answer) {
        int chineseChars = countChineseCharacters(answer);
        int latinLetters = countLatinLetters(answer);
        return chineseChars >= 4 || (chineseChars >= 2 && chineseChars * 2 >= latinLetters);
    }

    private boolean looksEnglishEnough(String answer) {
        int chineseChars = countChineseCharacters(answer);
        int latinLetters = countLatinLetters(answer);
        return latinLetters >= 6 && latinLetters >= chineseChars * 2;
    }

    private String rewriteAnswerInRequiredLanguage(
            ChatModel selectedModel,
            String answer,
            ResponseLanguage responseLanguage,
            boolean languageSwitchFollowUp
    ) {
        if (responseLanguage == ResponseLanguage.AUTO) {
            return answer;
        }
        String targetLanguage = responseLanguage == ResponseLanguage.CHINESE ? "Simplified Chinese" : "English";
        String rewriteInstruction = languageSwitchFollowUp
                ? "Rewrite the previous assistant answer into the required language."
                : "Rewrite the assistant answer so it strictly matches the required language.";
        String rewritten = ChatClient.builder(selectedModel).build()
                .prompt()
                .system("""
                        You are fixing the language of an assistant answer.
                        Output language is a hard requirement.
                        Return only the rewritten answer.
                        Do not add notes, explanations, XML tags, or meta commentary.
                        """)
                .user("""
                        Target language:
                        %s

                        Task:
                        %s

                        Original answer:
                        <ORIGINAL_ANSWER>
                        %s
                        </ORIGINAL_ANSWER>
                        """.formatted(targetLanguage, rewriteInstruction, answer))
                .call()
                .content();
        String sanitized = Objects.toString(rewritten, "").trim();
        if (requiresRepair(sanitized, responseLanguage)) {
            return answer;
        }
        return sanitized;
    }

    private boolean looksLikePromptLeak(String answer) {
        String value = Objects.toString(answer, "");
        return containsAny(value,
                "<CURRENT_USER_MESSAGE>", "<CONVERSATION_SUMMARY>", "<THREAD_SUMMARY>", "<THREAD_CONTEXT>",
                "<RECENT_CONVERSATION>", "<KNOWLEDGE_EVIDENCE>", "<ACTIVE_DOCUMENT>", "<TASK_MODE>",
                "<PREVIOUS_USER_MESSAGE>", "<PREVIOUS_ASSISTANT_ANSWER>",
                "Current user message:", "Preferred reply language:", "Conversation summary:",
                "Conversation background for reference only:", "Latest user instruction:", "Requested reply language:");
    }

    private boolean looksLikeMetaResponse(String answer) {
        String lower = Objects.toString(answer, "").toLowerCase(Locale.ROOT);
        if (containsAny(lower, "the current user message is", "the user is asking", "the user is referring to")) {
            return true;
        }
        if (looksRepetitive(answer)) {
            return true;
        }
        String[] lines = lower.split("\\R");
        if (lines.length < 4) {
            return false;
        }
        int repeatedAdjacentLines = 0;
        for (int i = 1; i < lines.length; i++) {
            String previous = lines[i - 1].trim();
            String current = lines[i].trim();
            if (!previous.isEmpty() && previous.equals(current)) {
                repeatedAdjacentLines++;
            }
        }
        return repeatedAdjacentLines >= 2;
    }

    private boolean looksRepetitive(String answer) {
        String value = Objects.toString(answer, "").trim();
        if (value.length() < 120) {
            return false;
        }
        String[] sentences = value.split("(?<=[.!?\\u3002\\uFF01\\uFF1F])\\s*");
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (String sentence : sentences) {
            String normalized = sentence.replaceAll("\\s+", "").trim();
            if (normalized.length() < 12) {
                continue;
            }
            int count = counts.merge(normalized, 1, Integer::sum);
            if (count >= 3) {
                return true;
            }
        }
        return false;
    }

    private String currentDateContext() {
        LocalDate today = LocalDate.now();
        String weekday = today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        return today + " (" + weekday + ")";
    }

    private boolean containsAny(String text, String... fragments) {
        String value = Objects.toString(text, "");
        for (String fragment : fragments) {
            if (value.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasRetainedConversationContext(SessionContextWindow contextWindow) {
        return contextWindow.latestTurn().isPresent() || contextWindow.summary().isPresent();
    }

    private boolean equalsAny(String text, String... candidates) {
        String value = Objects.toString(text, "");
        for (String candidate : candidates) {
            if (value.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String renderConversationSummary(SessionSummary summary) {
        String topic = Objects.toString(summary.topic(), "").trim();
        String body = Objects.toString(summary.summary(), "").trim();
        if (topic.isEmpty()) {
            return body;
        }
        if (body.isEmpty()) {
            return "Topic: " + topic;
        }
        return "Topic: " + topic + "\n" + body;
    }

    private String renderStrictLanguageInstruction(ResponseLanguage responseLanguage) {
        return switch (responseLanguage) {
            case CHINESE -> "Output language is a hard requirement for this turn. You must answer only in Simplified Chinese unless a proper noun needs to stay in its original form.\n";
            case ENGLISH -> "Output language is a hard requirement for this turn. You must answer only in English unless a proper noun needs to stay in its original form.\n";
            case AUTO -> "If no explicit language is requested, keep the answer in the same language as the latest user message.\n";
        };
    }

    private void logPromptDecision(
            CustomerMessage message,
            String currentQuestion,
            ResponseLanguage responseLanguage,
            boolean languageSwitchFollowUp,
            boolean ambiguousFollowUp,
            boolean hasRetainedConversationContext,
            boolean useConversationContext,
            TaskMode taskMode,
            Optional<ActiveDocument> activeDocument,
            SessionContextWindow contextWindow,
            String userPrompt
    ) {
        if (!logger.isDebugEnabled()) {
            return;
        }
        logger.debug(
                "answer.context sessionId={} userId={} question={} taskMode={} activeDocument={} language={} languageSwitchFollowUp={} ambiguousFollowUp={} hasRetainedConversationContext={} useConversationContext={} latestTurnPresent={} summaryPresent={} promptTurns={} recentTurns={} promptPreview={}",
                message.sessionId(),
                message.userId(),
                abbreviate(currentQuestion, 160),
                taskMode,
                activeDocument.map(ActiveDocument::documentId).orElse(""),
                responseLanguage,
                languageSwitchFollowUp,
                ambiguousFollowUp,
                hasRetainedConversationContext,
                useConversationContext,
                contextWindow.latestTurn().isPresent(),
                contextWindow.summary().isPresent(),
                contextWindow.promptTurns().size(),
                contextWindow.recentTurns().size(),
                abbreviate(userPrompt, 400)
        );
    }

    private String abbreviate(String text, int maxChars) {
        String value = Objects.toString(text, "").replaceAll("\\s+", " ").trim();
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(maxChars - 3, 1)) + "...";
    }

    private ChatModel selectChatModel() {
        String provider = Objects.toString(aiProviderProperties.provider(), "").trim().toLowerCase(Locale.ROOT);
        return switch (provider) {
            case "deepseek" -> requirePresent(deepSeekChatModelProvider.getIfAvailable(), "deepseek");
            case "qwen", "openai" -> requirePresent(openAiChatModelProvider.getIfAvailable(), "openai");
            case "minimax" -> requirePresent(miniMaxChatModelProvider.getIfAvailable(), "minimax");
            case "auto" -> autoSelectModel();
            default -> throw new IllegalArgumentException(
                    "Unsupported app.ai.provider='" + aiProviderProperties.provider()
                            + "'. Supported: auto, deepseek, qwen/openai, minimax"
            );
        };
    }

    private ChatModel autoSelectModel() {
        ChatModel miniMax = miniMaxChatModelProvider.getIfAvailable();
        if (miniMax != null) {
            return miniMax;
        }
        ChatModel deepSeek = deepSeekChatModelProvider.getIfAvailable();
        if (deepSeek != null) {
            return deepSeek;
        }
        ChatModel openAi = openAiChatModelProvider.getIfAvailable();
        if (openAi != null) {
            return openAi;
        }
        throw new IllegalStateException(
                "No chat model beans are available. Configure an API key for one provider "
                        + "(e.g. SPRING_AI_MINIMAX_API_KEY) or set app.ai.provider explicitly."
        );
    }

    private ChatModel requirePresent(ChatModel model, String expectedProvider) {
        if (model == null) {
            throw new IllegalStateException(
                    "Chat model bean for '" + expectedProvider + "' is not available. "
                            + "Check your Spring AI dependency and API key configuration."
            );
        }
        return model;
    }

    private RetrievedKnowledge retrieveKnowledgeIfEnabled(
            String sessionId,
            String query,
            Optional<ActiveDocument> activeDocument,
            TaskMode taskMode
    ) {
        if (!ragProperties.enabled()) {
            return RetrievedKnowledge.empty();
        }
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            return RetrievedKnowledge.empty();
        }
        List<Document> documents;
        try {
            String retrievalQuery = buildRetrievalQuery(query, activeDocument);
            SearchRequest.Builder builder = SearchRequest.builder()
                    .query(retrievalQuery)
                    .topK(ragProperties.topK());
            if (ragProperties.similarityThreshold() > 0) {
                builder = builder.similarityThreshold(ragProperties.similarityThreshold());
            }
            buildActiveDocumentFilter(activeDocument, query).ifPresent(builder::filterExpression);
            SearchRequest request = builder.build();
            documents = vectorStore.similaritySearch(request);
        } catch (Exception e) {
            return RetrievedKnowledge.empty();
        }
        if (documents == null || documents.isEmpty()) {
            return RetrievedKnowledge.empty();
        }
        List<String> snippets = new ArrayList<>(documents.size());
        List<String> citations = new ArrayList<>(documents.size());
        Optional<ActiveDocument> retrievedActiveDocument = Optional.empty();
        for (Document document : documents) {
            if (document == null) {
                continue;
            }
            String content = Objects.toString(document.getText(), "").trim();
            if (content.isEmpty()) {
                continue;
            }
            if (ragProperties.maxCharsPerDoc() > 0 && content.length() > ragProperties.maxCharsPerDoc()) {
                content = content.substring(0, ragProperties.maxCharsPerDoc()) + "...";
            }
            ActiveDocument documentFocus = toActiveDocument(sessionId, document);
            if (retrievedActiveDocument.isEmpty() && !documentFocus.documentId().isBlank()) {
                retrievedActiveDocument = Optional.of(documentFocus);
            }
            snippets.add(renderEvidenceSnippet(document, content));
            Object source = document.getMetadata() == null ? null : document.getMetadata().get("source");
            if (source != null) {
                citations.add(String.valueOf(source));
            }
        }
        if (snippets.isEmpty()) {
            return RetrievedKnowledge.empty();
        }
        String knowledgeText = String.join("\n", snippets);
        return new RetrievedKnowledge(knowledgeText, citations, retrievedActiveDocument);
    }

    private String buildRetrievalQuery(String query, Optional<ActiveDocument> activeDocument) {
        String value = Objects.toString(query, "").trim();
        if (activeDocument == null || activeDocument.isEmpty() || hasExplicitDocumentTitle(value)) {
            return value;
        }
        ActiveDocument document = activeDocument.orElseThrow();
        String documentHints = firstNonBlank(document.title(), document.sourceToken(), document.source(), document.documentId());
        if (documentHints.isBlank()) {
            return value;
        }
        return value + "\nCurrent document: " + documentHints;
    }

    private Optional<String> buildActiveDocumentFilter(Optional<ActiveDocument> activeDocument, String query) {
        if (activeDocument == null || activeDocument.isEmpty() || hasExplicitDocumentTitle(query)) {
            return Optional.empty();
        }
        ActiveDocument document = activeDocument.orElseThrow();
        if (!document.sourceToken().isBlank()) {
            return Optional.of("sourceToken == '" + escapeFilterValue(document.sourceToken()) + "'");
        }
        if (!document.source().isBlank()) {
            return Optional.of("source == '" + escapeFilterValue(document.source()) + "'");
        }
        if (!document.documentId().isBlank()) {
            return Optional.of("documentId == '" + escapeFilterValue(document.documentId()) + "'");
        }
        return Optional.empty();
    }

    @SuppressWarnings("unused")
    private Optional<String> buildActiveDocumentFilter(Optional<ActiveDocument> activeDocument, TaskMode taskMode) {
        return buildActiveDocumentFilter(activeDocument, "");
    }

    private boolean hasExplicitDocumentTitle(String query) {
        return EXPLICIT_DOCUMENT_TITLE_PATTERN.matcher(Objects.toString(query, "")).find();
    }

    private String escapeFilterValue(String value) {
        return Objects.toString(value, "").replace("\\", "\\\\").replace("'", "\\'");
    }

    private String renderEvidenceSnippet(Document document, String content) {
        String documentId = metadataValue(document, "documentId");
        String title = metadataValue(document, "title");
        String source = metadataValue(document, "source");
        String sourceToken = metadataValue(document, "sourceToken");
        Object chunkIndex = document.getMetadata() == null ? null : document.getMetadata().get("chunkIndex");
        return """
                [doc=%s][title=%s][sourceToken=%s][source=%s][chunk=%s]
                %s
                """.formatted(
                firstNonBlank(documentId, sourceToken, source, Objects.toString(document.getId(), "")),
                title,
                sourceToken,
                source,
                Objects.toString(chunkIndex, ""),
                content
        );
    }

    private ActiveDocument toActiveDocument(String sessionId, Document document) {
        String documentId = firstNonBlank(
                metadataValue(document, "documentId"),
                metadataValue(document, "sourceToken"),
                metadataValue(document, "source"),
                Objects.toString(document == null ? "" : document.getId(), "")
        );
        return new ActiveDocument(
                sessionId,
                documentId,
                metadataValue(document, "title"),
                metadataValue(document, "source"),
                metadataValue(document, "sourceToken"),
                java.time.Instant.now()
        );
    }

    private String metadataValue(Document document, String key) {
        if (document == null || document.getMetadata() == null) {
            return "";
        }
        return Objects.toString(document.getMetadata().get(key), "").trim();
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

    private record RetrievedKnowledge(
            String knowledgeText,
            List<String> citations,
            Optional<ActiveDocument> activeDocument
    ) {
        private static RetrievedKnowledge empty() {
            return new RetrievedKnowledge("", List.of(), Optional.empty());
        }
    }

    private enum ResponseLanguage {
        AUTO("same language as the latest user message"),
        CHINESE("Simplified Chinese"),
        ENGLISH("English");

        private final String description;

        ResponseLanguage(String description) {
            this.description = description;
        }
    }
}

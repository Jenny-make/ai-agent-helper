package com.example.customerservice.service;

import com.example.customerservice.config.FeishuProperties;
import com.example.customerservice.model.CustomerMessage;
import com.example.customerservice.model.ReplyResult;
import com.example.customerservice.model.feishu.FeishuWebhookRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class FeishuWebhookService {

    private static final Duration DELIVERY_DEDUP_TTL = Duration.ofMinutes(10);

    private final FeishuProperties feishuProperties;
    private final KnowledgeAnswerService knowledgeAnswerService;
    private final FeishuMessageService feishuMessageService;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, Instant> processedDeliveries = new ConcurrentHashMap<>();

    public FeishuWebhookService(
            FeishuProperties feishuProperties,
            KnowledgeAnswerService knowledgeAnswerService,
            FeishuMessageService feishuMessageService,
            ObjectMapper objectMapper
    ) {
        this.feishuProperties = feishuProperties;
        this.knowledgeAnswerService = knowledgeAnswerService;
        this.feishuMessageService = feishuMessageService;
        this.objectMapper = objectMapper;
    }

    public boolean isUrlVerification(FeishuWebhookRequest request) {
        return request != null && "url_verification".equalsIgnoreCase(Objects.toString(request.type(), ""));
    }

    public String getChallenge(FeishuWebhookRequest request) {
        return Objects.toString(request.challenge(), "");
    }

    public boolean verifyToken(FeishuWebhookRequest request) {
        String expected = Objects.toString(feishuProperties.verificationToken(), "").trim();
        if (expected.isEmpty()) {
            return true;
        }

        String requestToken = Objects.toString(request.token(), "").trim();
        if (requestToken.isEmpty() && request.header() != null) {
            requestToken = Objects.toString(request.header().token(), "").trim();
        }

        return expected.equals(requestToken);
    }

    public Optional<ReplyResult> handleEvent(FeishuWebhookRequest request) {
        if (!isSupportedMessageEvent(request)) {
            return Optional.empty();
        }

        if (isDuplicateDelivery(request)) {
            return Optional.empty();
        }

        ParsedIncomingMessage parsedIncomingMessage = parseIncomingMessage(request);
        if (parsedIncomingMessage == null || !shouldRespond(request, parsedIncomingMessage)) {
            return Optional.empty();
        }

        CustomerMessage customerMessage = toCustomerMessage(request, parsedIncomingMessage.text());
        if (customerMessage == null || customerMessage.text().isBlank()) {
            return Optional.empty();
        }

        ReplyResult result = knowledgeAnswerService.answer(customerMessage);
        feishuMessageService.replyText(request.event().message().messageId(), result.answer());
        return Optional.of(result);
    }

    private boolean isSupportedMessageEvent(FeishuWebhookRequest request) {
        if (request == null || request.header() == null || request.event() == null || request.event().message() == null) {
            return false;
        }

        String eventType = Objects.toString(request.header().eventType(), "").toLowerCase(Locale.ROOT);
        if (!"im.message.receive_v1".equals(eventType)) {
            return false;
        }

        String senderType = request.event().sender() == null ? "" : Objects.toString(request.event().sender().senderType(), "");
        if (!senderType.isBlank() && !"user".equalsIgnoreCase(senderType)) {
            return false;
        }

        String messageType = Objects.toString(request.event().message().messageType(), "");
        return "text".equalsIgnoreCase(messageType);
    }

    private CustomerMessage toCustomerMessage(FeishuWebhookRequest request, String text) {
        String userId = extractUserId(request);
        String sessionId = extractSessionId(request, userId);
        return new CustomerMessage(userId, sessionId, text);
    }

    private ParsedIncomingMessage parseIncomingMessage(FeishuWebhookRequest request) {
        String rawText = extractTextContent(request.event().message().content());
        if (rawText.isBlank()) {
            return null;
        }

        String sanitizedText = sanitizeIncomingText(rawText);
        return new ParsedIncomingMessage(rawText, sanitizedText);
    }

    private String extractTextContent(String rawContent) {
        String content = Objects.toString(rawContent, "").trim();
        if (content.isEmpty()) {
            return "";
        }

        try {
            Map<String, Object> map = objectMapper.readValue(content, new TypeReference<>() {});
            return Objects.toString(map.get("text"), "").trim();
        } catch (Exception e) {
            return "";
        }
    }

    private String sanitizeIncomingText(String rawText) {
        String sanitized = Objects.toString(rawText, "")
                .replace('\u00A0', ' ')
                .trim();

        while (sanitized.startsWith("@")) {
            int mentionEnd = findLeadingMentionEnd(sanitized);
            if (mentionEnd <= 0) {
                break;
            }
            sanitized = sanitized.substring(mentionEnd).trim();
        }

        return sanitized.replaceAll("\\s+", " ").trim();
    }

    private int findLeadingMentionEnd(String text) {
        int index = 0;
        while (index < text.length() && !Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private boolean shouldRespond(FeishuWebhookRequest request, ParsedIncomingMessage parsedIncomingMessage) {
        if (parsedIncomingMessage == null || parsedIncomingMessage.text().isBlank()) {
            return false;
        }

        String chatType = request.event() == null || request.event().message() == null
                ? ""
                : Objects.toString(request.event().message().chatType(), "").trim().toLowerCase(Locale.ROOT);

        if ("p2p".equals(chatType)) {
            return true;
        }

        return looksLikeBotMention(parsedIncomingMessage.rawText());
    }

    private boolean looksLikeBotMention(String rawText) {
        String value = Objects.toString(rawText, "").stripLeading();
        return value.startsWith("@");
    }

    private String extractUserId(FeishuWebhookRequest request) {
        if (request.event() == null || request.event().sender() == null || request.event().sender().senderId() == null) {
            return "feishu-user";
        }

        FeishuWebhookRequest.SenderId senderId = request.event().sender().senderId();
        if (senderId.openId() != null && !senderId.openId().isBlank()) {
            return senderId.openId();
        }
        if (senderId.userId() != null && !senderId.userId().isBlank()) {
            return senderId.userId();
        }
        if (senderId.unionId() != null && !senderId.unionId().isBlank()) {
            return senderId.unionId();
        }
        return "feishu-user";
    }

    private String extractSessionId(FeishuWebhookRequest request, String userId) {
        if (request.event() != null && request.event().message() != null) {
            String normalizedUserId = normalizeSessionComponent(userId);
            String rootId = Objects.toString(request.event().message().rootId(), "").trim();
            if (!rootId.isEmpty()) {
                return "feishu-thread-" + normalizeSessionComponent(rootId) + "-user-" + normalizedUserId;
            }
            String chatId = Objects.toString(request.event().message().chatId(), "").trim();
            if (!chatId.isEmpty()) {
                return "feishu-chat-" + normalizeSessionComponent(chatId) + "-user-" + normalizedUserId;
            }
            String messageId = Objects.toString(request.event().message().messageId(), "").trim();
            if (!messageId.isEmpty()) {
                return "feishu-message-" + normalizeSessionComponent(messageId) + "-user-" + normalizedUserId;
            }
        }
        return "feishu-session";
    }

    private String normalizeSessionComponent(String value) {
        return Objects.toString(value, "")
                .trim()
                .replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private boolean isDuplicateDelivery(FeishuWebhookRequest request) {
        cleanupProcessedDeliveries();

        String deliveryId = extractDeliveryId(request);
        if (deliveryId.isBlank()) {
            return false;
        }

        Instant now = Instant.now();
        Instant previous = processedDeliveries.putIfAbsent(deliveryId, now);
        return previous != null;
    }

    private void cleanupProcessedDeliveries() {
        Instant threshold = Instant.now().minus(DELIVERY_DEDUP_TTL);
        processedDeliveries.entrySet().removeIf(entry -> entry.getValue().isBefore(threshold));
    }

    private String extractDeliveryId(FeishuWebhookRequest request) {
        List<String> candidates = List.of(
                request != null && request.header() != null ? Objects.toString(request.header().eventId(), "").trim() : "",
                request != null && request.event() != null && request.event().message() != null
                        ? Objects.toString(request.event().message().messageId(), "").trim()
                        : ""
        );

        for (String candidate : candidates) {
            if (!candidate.isEmpty()) {
                return candidate;
            }
        }
        return "";
    }

    private record ParsedIncomingMessage(String rawText, String text) {
    }
}

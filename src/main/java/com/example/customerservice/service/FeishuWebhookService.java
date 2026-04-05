package com.example.customerservice.service;

import com.example.customerservice.config.FeishuProperties;
import com.example.customerservice.model.CustomerMessage;
import com.example.customerservice.model.ReplyResult;
import com.example.customerservice.model.feishu.FeishuWebhookRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class FeishuWebhookService {

    private final FeishuProperties feishuProperties;
    private final KnowledgeAnswerService knowledgeAnswerService;
    private final FeishuMessageService feishuMessageService;
    private final ObjectMapper objectMapper;

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

        CustomerMessage customerMessage = toCustomerMessage(request);
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

    private CustomerMessage toCustomerMessage(FeishuWebhookRequest request) {
        String text = extractTextContent(request.event().message().content());
        String userId = extractUserId(request);
        String sessionId = extractSessionId(request);
        return new CustomerMessage(userId, sessionId, text);
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

    private String extractSessionId(FeishuWebhookRequest request) {
        if (request.event() != null && request.event().message() != null) {
            String chatId = Objects.toString(request.event().message().chatId(), "").trim();
            if (!chatId.isEmpty()) {
                return "feishu-chat-" + chatId;
            }
            String rootId = Objects.toString(request.event().message().rootId(), "").trim();
            if (!rootId.isEmpty()) {
                return "feishu-thread-" + rootId;
            }
            String messageId = Objects.toString(request.event().message().messageId(), "").trim();
            if (!messageId.isEmpty()) {
                return "feishu-message-" + messageId;
            }
        }
        return "feishu-session";
    }
}

package com.example.customerservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.customerservice.config.FeishuProperties;
import com.example.customerservice.model.CustomerMessage;
import com.example.customerservice.model.ReplyResult;
import com.example.customerservice.model.feishu.FeishuWebhookRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FeishuWebhookServiceTest {

    @Test
    void groupMessageWithoutMentionIsIgnored() {
        KnowledgeAnswerService knowledgeAnswerService = mock(KnowledgeAnswerService.class);
        FeishuMessageService feishuMessageService = mock(FeishuMessageService.class);
        FeishuWebhookService service = new FeishuWebhookService(
                new FeishuProperties("https://open.feishu.cn/open-apis", "", "", null),
                knowledgeAnswerService,
                feishuMessageService,
                mock(ConversationMemoryService.class),
                new ObjectMapper()
        );

        var result = service.handleEvent(buildRequest("group", "大家好", "evt-1", "msg-1", "chat-1", "", "user-1"));

        assertTrue(result.isEmpty());
        verify(knowledgeAnswerService, never()).answer(any());
        verify(feishuMessageService, never()).replyText(any(), any());
    }

    @Test
    void groupMentionMessageIsSanitizedBeforeAnswering() {
        KnowledgeAnswerService knowledgeAnswerService = mock(KnowledgeAnswerService.class);
        FeishuMessageService feishuMessageService = mock(FeishuMessageService.class);
        FeishuWebhookService service = new FeishuWebhookService(
                new FeishuProperties("https://open.feishu.cn/open-apis", "", "", null),
                knowledgeAnswerService,
                feishuMessageService,
                mock(ConversationMemoryService.class),
                new ObjectMapper()
        );
        when(knowledgeAnswerService.answer(any())).thenReturn(new ReplyResult("Hello there", "test", List.of()));

        var result = service.handleEvent(buildRequest("group", "@_user_1 hello", "evt-2", "msg-2", "chat-2", "", "user-2"));

        assertTrue(result.isPresent());

        ArgumentCaptor<CustomerMessage> captor = ArgumentCaptor.forClass(CustomerMessage.class);
        verify(knowledgeAnswerService).answer(captor.capture());
        CustomerMessage customerMessage = captor.getValue();
        assertEquals("hello", customerMessage.text());
        assertEquals("feishu-chat-chat-2-user-user-2", customerMessage.sessionId());
        verify(feishuMessageService).replyText("msg-2", "Hello there");
    }

    @Test
    void mentionWithoutWhitespaceIsSanitizedUsingMentionMetadata() {
        KnowledgeAnswerService knowledgeAnswerService = mock(KnowledgeAnswerService.class);
        FeishuMessageService feishuMessageService = mock(FeishuMessageService.class);
        FeishuWebhookService service = new FeishuWebhookService(
                new FeishuProperties("https://open.feishu.cn/open-apis", "", "", null),
                knowledgeAnswerService,
                feishuMessageService,
                mock(ConversationMemoryService.class),
                new ObjectMapper()
        );
        when(knowledgeAnswerService.answer(any())).thenReturn(new ReplyResult("Java GC answer", "test", List.of()));

        String content = "{\"text\":\"@智能助手了解java gc吗\",\"mentions\":[{\"key\":\"@智能助手\",\"name\":\"智能助手\"}]}";
        FeishuWebhookRequest request = buildRequest("group", content, "evt-2b", "msg-2b", "chat-2", "", "user-2", true);

        var result = service.handleEvent(request);

        assertTrue(result.isPresent());

        ArgumentCaptor<CustomerMessage> captor = ArgumentCaptor.forClass(CustomerMessage.class);
        verify(knowledgeAnswerService).answer(captor.capture());
        assertEquals("了解java gc吗", captor.getValue().text());
    }

    @Test
    void duplicateDeliveryIsIgnored() {
        KnowledgeAnswerService knowledgeAnswerService = mock(KnowledgeAnswerService.class);
        FeishuMessageService feishuMessageService = mock(FeishuMessageService.class);
        FeishuWebhookService service = new FeishuWebhookService(
                new FeishuProperties("https://open.feishu.cn/open-apis", "", "", null),
                knowledgeAnswerService,
                feishuMessageService,
                mock(ConversationMemoryService.class),
                new ObjectMapper()
        );
        when(knowledgeAnswerService.answer(any())).thenReturn(new ReplyResult("Hi", "test", List.of()));

        FeishuWebhookRequest request = buildRequest("group", "@_user_1 hello", "evt-3", "msg-3", "chat-3", "", "user-3");
        service.handleEvent(request);
        service.handleEvent(request);

        verify(knowledgeAnswerService).answer(any());
        verify(feishuMessageService).replyText(eq("msg-3"), eq("Hi"));
    }

    @Test
    void exactNewCommandClearsCurrentSessionWithoutCallingModel() {
        KnowledgeAnswerService knowledgeAnswerService = mock(KnowledgeAnswerService.class);
        FeishuMessageService feishuMessageService = mock(FeishuMessageService.class);
        ConversationMemoryService conversationMemoryService = mock(ConversationMemoryService.class);
        FeishuWebhookService service = new FeishuWebhookService(
                new FeishuProperties("https://open.feishu.cn/open-apis", "", "", null),
                knowledgeAnswerService,
                feishuMessageService,
                conversationMemoryService,
                new ObjectMapper()
        );

        var result = service.handleEvent(buildRequest("p2p", "/new", "evt-4", "msg-4", "chat-4", "", "user-4"));

        assertTrue(result.isPresent());
        assertEquals("已创建新会话，我会从这里重新开始。", result.orElseThrow().answer());
        verify(conversationMemoryService).clearSession("feishu-chat-chat-4-user-user-4");
        verify(knowledgeAnswerService, never()).answer(any());
        verify(feishuMessageService).replyText("msg-4", "已创建新会话，我会从这里重新开始。");
    }

    @Test
    void newCommandOnlyMatchesExactText() {
        KnowledgeAnswerService knowledgeAnswerService = mock(KnowledgeAnswerService.class);
        FeishuMessageService feishuMessageService = mock(FeishuMessageService.class);
        ConversationMemoryService conversationMemoryService = mock(ConversationMemoryService.class);
        FeishuWebhookService service = new FeishuWebhookService(
                new FeishuProperties("https://open.feishu.cn/open-apis", "", "", null),
                knowledgeAnswerService,
                feishuMessageService,
                conversationMemoryService,
                new ObjectMapper()
        );
        when(knowledgeAnswerService.answer(any())).thenReturn(new ReplyResult("normal answer", "test", List.of()));

        var result = service.handleEvent(buildRequest("p2p", "/new please", "evt-5", "msg-5", "chat-5", "", "user-5"));

        assertTrue(result.isPresent());
        verify(conversationMemoryService, never()).clearSession(any());
        verify(knowledgeAnswerService).answer(any());
        verify(feishuMessageService).replyText("msg-5", "normal answer");
    }

    private FeishuWebhookRequest buildRequest(
            String chatType,
            String text,
            String eventId,
            String messageId,
            String chatId,
            String rootId,
            String userId
    ) {
        return buildRequest(chatType, text, eventId, messageId, chatId, rootId, userId, false);
    }

    private FeishuWebhookRequest buildRequest(
            String chatType,
            String content,
            String eventId,
            String messageId,
            String chatId,
            String rootId,
            String userId,
            boolean rawContent
    ) {
        return new FeishuWebhookRequest(
                null,
                null,
                null,
                null,
                new FeishuWebhookRequest.Header(null, "im.message.receive_v1", eventId, null, null, null),
                new FeishuWebhookRequest.Event(
                        new FeishuWebhookRequest.Sender(
                                new FeishuWebhookRequest.SenderId(null, userId, null),
                                "user",
                                null
                        ),
                        new FeishuWebhookRequest.Message(
                                messageId,
                                rootId,
                                null,
                                null,
                                chatId,
                                chatType,
                                "text",
                                rawContent ? content : "{\"text\":\"" + content + "\"}"
                        )
                )
        );
    }
}

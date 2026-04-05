package com.example.customerservice.model.feishu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FeishuWebhookRequest(
        String schema,
        String type,
        String challenge,
        String token,
        Header header,
        Event event
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(
            String token,
            @JsonProperty("event_type") String eventType,
            @JsonProperty("event_id") String eventId,
            @JsonProperty("create_time") String createTime,
            @JsonProperty("app_id") String appId,
            @JsonProperty("tenant_key") String tenantKey
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Event(
            Sender sender,
            Message message
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Sender(
            @JsonProperty("sender_id") SenderId senderId,
            @JsonProperty("sender_type") String senderType,
            @JsonProperty("tenant_key") String tenantKey
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SenderId(
            @JsonProperty("open_id") String openId,
            @JsonProperty("user_id") String userId,
            @JsonProperty("union_id") String unionId
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
            @JsonProperty("message_id") String messageId,
            @JsonProperty("root_id") String rootId,
            @JsonProperty("parent_id") String parentId,
            @JsonProperty("create_time") String createTime,
            @JsonProperty("chat_id") String chatId,
            @JsonProperty("chat_type") String chatType,
            @JsonProperty("message_type") String messageType,
            String content
    ) {
    }
}

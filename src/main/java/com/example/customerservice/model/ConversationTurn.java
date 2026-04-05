package com.example.customerservice.model;

public record ConversationTurn(
        String userMessage,
        String assistantMessage
) {
}

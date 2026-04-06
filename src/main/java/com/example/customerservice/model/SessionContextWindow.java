package com.example.customerservice.model;

import java.util.List;
import java.util.Optional;

public record SessionContextWindow(
        String sessionId,
        List<SessionMessage> recentMessages,
        List<ConversationTurn> recentTurns,
        List<ConversationTurn> promptTurns,
        Optional<ConversationTurn> latestTurn,
        Optional<SessionSummary> summary
) {
    public SessionContextWindow {
        recentMessages = recentMessages == null ? List.of() : List.copyOf(recentMessages);
        recentTurns = recentTurns == null ? List.of() : List.copyOf(recentTurns);
        promptTurns = promptTurns == null ? List.of() : List.copyOf(promptTurns);
        latestTurn = latestTurn == null ? Optional.empty() : latestTurn;
        summary = summary == null ? Optional.empty() : summary;
    }

    public static SessionContextWindow empty(String sessionId) {
        return new SessionContextWindow(
                sessionId,
                List.of(),
                List.of(),
                List.of(),
                Optional.empty(),
                Optional.empty()
        );
    }
}

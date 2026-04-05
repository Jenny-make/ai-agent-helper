package com.example.customerservice.service;

import com.example.customerservice.config.ConversationMemoryProperties;
import com.example.customerservice.model.ConversationTurn;
import java.util.Arrays;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

@Service
public class ConversationMemoryService {

    private final ConversationMemoryProperties properties;
    private final ConcurrentMap<String, Deque<ConversationTurn>> sessions = new ConcurrentHashMap<>();

    public ConversationMemoryService(ConversationMemoryProperties properties) {
        this.properties = properties;
    }

    public List<ConversationTurn> getRecentTurns(String sessionId) {
        if (!properties.enabled()) {
            return List.of();
        }

        Deque<ConversationTurn> turns = sessions.get(sessionId);
        if (turns == null) {
            return List.of();
        }

        synchronized (turns) {
            return List.copyOf(turns);
        }
    }

    public void appendExchange(String sessionId, String userMessage, String assistantMessage) {
        if (!properties.enabled()) {
            return;
        }

        Deque<ConversationTurn> turns = sessions.computeIfAbsent(sessionId, ignored -> new ArrayDeque<>());
        ConversationTurn turn = new ConversationTurn(
                truncate(userMessage),
                truncate(assistantMessage)
        );

        synchronized (turns) {
            turns.addLast(turn);
            while (turns.size() > Math.max(properties.maxTurns(), 1)) {
                turns.removeFirst();
            }
        }
    }

    public void clearSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        sessions.remove(sessionId);
    }

    public Optional<ConversationTurn> getLatestTurn(String sessionId) {
        List<ConversationTurn> turns = getRecentTurns(sessionId);
        if (turns.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(turns.get(turns.size() - 1));
    }

    public Optional<String> inferTopicSummary(String sessionId) {
        return getLatestTurn(sessionId)
                .map(turn -> firstNonBlank(
                        firstSentence(turn.assistantMessage()),
                        firstSentence(turn.userMessage())
                ))
                .filter(text -> !text.isBlank());
    }

    public String formatHistory(String sessionId) {
        List<ConversationTurn> turns = getRecentTurns(sessionId);
        if (turns.isEmpty()) {
            return "(empty)";
        }

        List<String> lines = new ArrayList<>();
        for (int i = 0; i < turns.size(); i++) {
            ConversationTurn turn = turns.get(i);
            lines.add("#" + (i + 1) + " User: " + Objects.toString(turn.userMessage(), ""));
            lines.add("#" + (i + 1) + " Assistant: " + Objects.toString(turn.assistantMessage(), ""));
        }
        return String.join(System.lineSeparator(), lines);
    }

    private String truncate(String text) {
        String value = Objects.toString(text, "").trim();
        int maxChars = Math.max(properties.maxCharsPerMessage(), 1);
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "...";
    }

    private String firstSentence(String text) {
        String value = Objects.toString(text, "").trim();
        if (value.isEmpty()) {
            return "";
        }

        return Arrays.stream(value.split("(?<=[.!?。！？])\\s*", 2))
                .findFirst()
                .orElse(value)
                .trim();
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return "";
    }
}

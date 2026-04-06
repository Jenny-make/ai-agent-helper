package com.example.customerservice.service;

import com.example.customerservice.config.ConversationMemoryProperties;
import com.example.customerservice.model.ConversationTurn;
import com.example.customerservice.model.SessionContextWindow;
import com.example.customerservice.model.SessionMessage;
import com.example.customerservice.model.SessionRole;
import com.example.customerservice.model.SessionSummary;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ConversationMemoryService {

    private final ConversationMemoryProperties properties;
    private final ConcurrentMap<String, SessionMemory> sessions = new ConcurrentHashMap<>();

    public ConversationMemoryService(ConversationMemoryProperties properties) {
        this.properties = properties;
    }

    public SessionContextWindow getContextWindow(String sessionId) {
        String normalizedSessionId = normalizeSessionId(sessionId);
        if (!properties.enabled() || normalizedSessionId.isBlank()) {
            return SessionContextWindow.empty(normalizedSessionId);
        }

        SessionMemory memory = sessions.get(normalizedSessionId);
        if (memory == null) {
            return SessionContextWindow.empty(normalizedSessionId);
        }

        synchronized (memory) {
            List<SessionMessage> recentMessages = List.copyOf(memory.messages);
            List<ConversationTurn> recentTurns = toConversationTurns(recentMessages);
            int promptTurnLimit = resolvePromptTurnLimit();
            int splitIndex = Math.max(recentTurns.size() - promptTurnLimit, 0);
            List<ConversationTurn> promptTurns = List.copyOf(recentTurns.subList(splitIndex, recentTurns.size()));
            Optional<ConversationTurn> latestTurn = recentTurns.isEmpty()
                    ? Optional.empty()
                    : Optional.of(recentTurns.get(recentTurns.size() - 1));
            Optional<SessionSummary> summary = Optional.ofNullable(
                    buildSummarySnapshot(normalizedSessionId, memory.rolledSummary, recentTurns.subList(0, splitIndex))
            ).filter(SessionSummary::hasContent);

            return new SessionContextWindow(
                    normalizedSessionId,
                    recentMessages,
                    recentTurns,
                    promptTurns,
                    latestTurn,
                    summary
            );
        }
    }

    public List<ConversationTurn> getRecentTurns(String sessionId) {
        return getContextWindow(sessionId).recentTurns();
    }

    public List<SessionMessage> getRecentMessages(String sessionId) {
        return getContextWindow(sessionId).recentMessages();
    }

    public void appendExchange(String sessionId, String userMessage, String assistantMessage) {
        String normalizedSessionId = normalizeSessionId(sessionId);
        if (!properties.enabled() || normalizedSessionId.isBlank()) {
            return;
        }

        SessionMemory memory = sessions.computeIfAbsent(normalizedSessionId, ignored -> new SessionMemory());

        synchronized (memory) {
            memory.messages.addLast(buildMessage(normalizedSessionId, SessionRole.USER, userMessage));
            memory.messages.addLast(buildMessage(normalizedSessionId, SessionRole.ASSISTANT, assistantMessage));
            compactStoredMessages(normalizedSessionId, memory);
        }
    }

    public void clearSession(String sessionId) {
        String normalizedSessionId = normalizeSessionId(sessionId);
        if (normalizedSessionId.isBlank()) {
            return;
        }
        sessions.remove(normalizedSessionId);
    }

    public Optional<ConversationTurn> getLatestTurn(String sessionId) {
        return getContextWindow(sessionId).latestTurn();
    }

    public Optional<SessionSummary> getSessionSummary(String sessionId) {
        return getContextWindow(sessionId).summary();
    }

    public Optional<String> inferTopicSummary(String sessionId) {
        SessionContextWindow contextWindow = getContextWindow(sessionId);
        return contextWindow.summary()
                .map(summary -> firstNonBlank(summary.topic(), firstSentence(summary.summary())))
                .filter(text -> !text.isBlank())
                .or(() -> contextWindow.latestTurn().map(turn -> firstNonBlank(
                        firstSentence(turn.userMessage()),
                        firstSentence(turn.assistantMessage())
                )).filter(text -> !text.isBlank()));
    }

    public String formatHistory(String sessionId) {
        SessionContextWindow contextWindow = getContextWindow(sessionId);
        if (contextWindow.recentTurns().isEmpty() && contextWindow.summary().isEmpty()) {
            return "(empty)";
        }

        List<String> lines = new ArrayList<>();
        contextWindow.summary().ifPresent(summary -> {
            lines.add("[Summary]");
            if (!Objects.toString(summary.topic(), "").isBlank()) {
                lines.add("Topic: " + summary.topic());
            }
            if (!Objects.toString(summary.summary(), "").isBlank()) {
                lines.add(summary.summary());
            }
            lines.add("");
        });

        List<ConversationTurn> turns = contextWindow.recentTurns();
        for (int i = 0; i < turns.size(); i++) {
            ConversationTurn turn = turns.get(i);
            lines.add("#" + (i + 1) + " User: " + Objects.toString(turn.userMessage(), ""));
            lines.add("#" + (i + 1) + " Assistant: " + Objects.toString(turn.assistantMessage(), ""));
        }
        return String.join(System.lineSeparator(), lines);
    }

    private void compactStoredMessages(String sessionId, SessionMemory memory) {
        int maxMessages = Math.max(properties.maxTurns(), 1) * 2;
        while (memory.messages.size() > maxMessages) {
            ConversationTurn removedTurn = pollOldestTurn(memory.messages);
            if (removedTurn == null) {
                break;
            }
            memory.rolledSummary = buildSummarySnapshot(sessionId, memory.rolledSummary, List.of(removedTurn));
        }
    }

    private SessionMessage buildMessage(String sessionId, SessionRole role, String content) {
        return new SessionMessage(
                UUID.randomUUID().toString(),
                sessionId,
                role,
                truncate(content),
                Instant.now(),
                Map.of("source", "conversation-memory")
        );
    }

    private List<ConversationTurn> toConversationTurns(List<SessionMessage> messages) {
        List<ConversationTurn> turns = new ArrayList<>();
        for (int i = 0; i < messages.size(); i += 2) {
            SessionMessage first = messages.get(i);
            SessionMessage second = i + 1 < messages.size() ? messages.get(i + 1) : null;
            turns.add(new ConversationTurn(
                    contentForRole(first, second, SessionRole.USER),
                    contentForRole(first, second, SessionRole.ASSISTANT)
            ));
        }
        return turns;
    }

    private String contentForRole(SessionMessage first, SessionMessage second, SessionRole expectedRole) {
        if (first != null && first.role() == expectedRole) {
            return Objects.toString(first.content(), "");
        }
        if (second != null && second.role() == expectedRole) {
            return Objects.toString(second.content(), "");
        }
        return "";
    }

    private ConversationTurn pollOldestTurn(Deque<SessionMessage> messages) {
        if (messages.isEmpty()) {
            return null;
        }
        SessionMessage first = messages.pollFirst();
        SessionMessage second = messages.pollFirst();
        return new ConversationTurn(
                contentForRole(first, second, SessionRole.USER),
                contentForRole(first, second, SessionRole.ASSISTANT)
        );
    }

    private SessionSummary buildSummarySnapshot(
            String sessionId,
            SessionSummary baseSummary,
            List<ConversationTurn> additionalTurns
    ) {
        String topic = baseSummary == null ? "" : Objects.toString(baseSummary.topic(), "").trim();
        int version = baseSummary == null ? 0 : Math.max(baseSummary.version(), 0);
        List<String> lines = new ArrayList<>();
        if (baseSummary != null && !Objects.toString(baseSummary.summary(), "").isBlank()) {
            lines.addAll(Arrays.stream(baseSummary.summary().split("\\R"))
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .toList());
        }

        for (ConversationTurn turn : additionalTurns) {
            topic = firstNonBlank(topic, firstSentence(turn.userMessage()), firstSentence(turn.assistantMessage()));
            addSummaryLine(lines, "User asked", turn.userMessage());
            addSummaryLine(lines, "Assistant answered", turn.assistantMessage());
            version++;
        }

        String summaryText = trimSummaryLines(lines);
        if (topic.isBlank() && summaryText.isBlank()) {
            return null;
        }

        return new SessionSummary(sessionId, version, topic, summaryText, Instant.now());
    }

    private void addSummaryLine(List<String> lines, String label, String text) {
        String sentence = firstSentence(text);
        if (!sentence.isBlank()) {
            lines.add("- " + label + ": " + sentence);
        }
    }

    private String trimSummaryLines(List<String> lines) {
        List<String> normalized = lines.stream()
                .map(line -> Objects.toString(line, "").trim())
                .filter(line -> !line.isBlank())
                .collect(Collectors.toCollection(ArrayList::new));
        if (normalized.isEmpty()) {
            return "";
        }

        int maxChars = Math.max(properties.maxSummaryChars(), 1);
        String joined = String.join("\n", normalized);
        if (joined.length() <= maxChars) {
            return joined;
        }

        List<String> kept = new ArrayList<>();
        int usedChars = 0;
        for (int i = normalized.size() - 1; i >= 0; i--) {
            String candidate = normalized.get(i);
            if (candidate.length() > maxChars) {
                candidate = candidate.substring(0, Math.max(maxChars - 3, 1)) + "...";
            }

            int additionalChars = kept.isEmpty() ? candidate.length() : candidate.length() + 1;
            if (usedChars + additionalChars > maxChars) {
                continue;
            }

            kept.add(0, candidate);
            usedChars += additionalChars;
        }

        return String.join("\n", kept);
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

        return Arrays.stream(value.split("(?<=[.!?\\u3002\\uFF01\\uFF1F])\\s*", 2))
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

    private int resolvePromptTurnLimit() {
        int maxTurns = Math.max(properties.maxTurns(), 1);
        int promptTurns = Math.max(properties.promptRecentTurns(), 1);
        return Math.min(promptTurns, maxTurns);
    }

    private String normalizeSessionId(String sessionId) {
        return Objects.toString(sessionId, "").trim();
    }

    private static final class SessionMemory {
        private final Deque<SessionMessage> messages = new ArrayDeque<>();
        private SessionSummary rolledSummary;
    }
}

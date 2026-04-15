package com.example.customerservice.service;

import com.example.customerservice.model.ActiveDocument;
import com.example.customerservice.model.TaskMode;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class TaskModeClassifier {

    public TaskMode classify(String question, Optional<ActiveDocument> activeDocument) {
        String value = Objects.toString(question, "").trim();
        if (value.isBlank()) {
            return TaskMode.SMALL_TALK;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        String compactChinese = value.replaceAll("\\s+", "");

        if (containsAny(normalized, "debug=true", "debug rag", "only retrieved", "retrieval result")
                || containsAny(compactChinese,
                "\u53ea\u57fa\u4e8e\u68c0\u7d22",
                "\u53ea\u6839\u636e\u68c0\u7d22",
                "\u8c03\u8bd5\u68c0\u7d22")) {
            return TaskMode.DEBUG_RAG;
        }

        if (isDocumentFollowUp(normalized, compactChinese, activeDocument)) {
            return TaskMode.FOLLOW_UP_ON_DOCUMENT;
        }

        if (isDocumentQuestion(normalized, compactChinese)) {
            return TaskMode.DOCUMENT_QA;
        }

        if (containsAny(normalized, "my preference", "remember that", "for me")
                || containsAny(compactChinese,
                "\u4ee5\u540e\u9ed8\u8ba4",
                "\u6211\u7684\u504f\u597d",
                "\u8bb0\u4f4f\u6211")) {
            return TaskMode.PERSONALIZED_CHAT;
        }

        return TaskMode.SMALL_TALK;
    }

    private boolean isDocumentFollowUp(
            String normalized,
            String compactChinese,
            Optional<ActiveDocument> activeDocument
    ) {
        if (activeDocument.isEmpty()) {
            return false;
        }
        if (containsAny(normalized,
                "this document", "that document", "this paper", "that paper",
                "this paragraph", "that paragraph", "above", "previous paragraph",
                "second author", "first author", "what about it", "continue")) {
            return true;
        }
        return containsAny(compactChinese,
                "\u8fd9\u7bc7",
                "\u8be5\u6587\u6863",
                "\u8fd9\u4e2a\u6587\u6863",
                "\u90a3\u7bc7",
                "\u8fd9\u4efd\u6587\u6863",
                "\u8fd9\u7bc7\u6587\u7ae0",
                "\u8fd9\u7bc7\u4f5c\u6587",
                "\u8fd9\u7bc7\u62a5\u544a",
                "\u8fd9\u6bb5",
                "\u4e0a\u4e00\u6bb5",
                "\u4e0a\u6587",
                "\u7b2c\u4e8c\u4e2a\u4f5c\u8005",
                "\u7b2c\u4e00\u4f5c\u8005",
                "\u6307\u5bfc\u8001\u5e08",
                "\u81ea\u6211\u8bc4\u4ef7",
                "\u4ed6\u4eba\u8bc4\u4ef7",
                "\u5176\u4ed6\u4eba\u8bc4\u4ef7",
                "\u7ee7\u7eed",
                "\u5c55\u5f00\u8bf4\u8bf4",
                "\u8be6\u7ec6\u70b9",
                "\u5b83\u7684",
                "\u5176\u4e2d");
    }

    private boolean isDocumentQuestion(String normalized, String compactChinese) {
        return containsAny(normalized,
                "document", "paper", "article", "author", "citation", "source",
                "according to", "based on")
                || containsAny(compactChinese,
                "\u6587\u6863",
                "\u8bba\u6587",
                "\u6587\u7ae0",
                "\u4f5c\u6587",
                "\u62a5\u544a",
                "\u4f5c\u8005",
                "\u5f15\u7528",
                "\u51fa\u5904",
                "\u6839\u636e",
                "\u57fa\u4e8e",
                "\u77e5\u8bc6\u5e93");
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
}

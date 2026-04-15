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
                || containsAny(compactChinese, "只基于检索", "只根据检索", "调试检索")) {
            return TaskMode.DEBUG_RAG;
        }

        if (isDocumentFollowUp(normalized, compactChinese, activeDocument)) {
            return TaskMode.FOLLOW_UP_ON_DOCUMENT;
        }

        if (isDocumentQuestion(normalized, compactChinese)) {
            return TaskMode.DOCUMENT_QA;
        }

        if (containsAny(normalized, "my preference", "remember that", "for me")
                || containsAny(compactChinese, "以后默认", "我的偏好", "记住我")) {
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
                "这篇", "该文档", "这个文档", "那篇", "这份文档",
                "这段", "上一段", "上文", "第二个作者", "第一作者",
                "继续", "展开说说", "详细点", "它的", "其中");
    }

    private boolean isDocumentQuestion(String normalized, String compactChinese) {
        return containsAny(normalized,
                "document", "paper", "article", "author", "citation", "source",
                "according to", "based on")
                || containsAny(compactChinese,
                "文档", "论文", "文章", "作者", "引用", "出处", "根据", "基于", "知识库");
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

package com.example.customerservice.service;

import com.example.customerservice.config.AiProviderProperties;
import com.example.customerservice.config.RagProperties;
import com.example.customerservice.model.CustomerMessage;
import com.example.customerservice.model.ReplyResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeAnswerService {

    private final AiProviderProperties aiProviderProperties;
    private final RagProperties ragProperties;
    private final DeepSeekChatModel deepSeekChatModel;
    private final OpenAiChatModel openAiChatModel;
    private final ObjectProvider<VectorStore> vectorStoreProvider;

    public KnowledgeAnswerService(
            AiProviderProperties aiProviderProperties,
            RagProperties ragProperties,
            DeepSeekChatModel deepSeekChatModel,
            OpenAiChatModel openAiChatModel,
            ObjectProvider<VectorStore> vectorStoreProvider
    ) {
        this.aiProviderProperties = aiProviderProperties;
        this.ragProperties = ragProperties;
        this.deepSeekChatModel = deepSeekChatModel;
        this.openAiChatModel = openAiChatModel;
        this.vectorStoreProvider = vectorStoreProvider;
    }

    public ReplyResult answer(CustomerMessage message) {
        var selectedModel = "qwen".equalsIgnoreCase(aiProviderProperties.provider())
                ? openAiChatModel
                : deepSeekChatModel;

        RetrievedKnowledge retrievedKnowledge = retrieveKnowledgeIfEnabled(message.text());

        ChatClient chatClient = ChatClient.builder(selectedModel).build();

        String answer = chatClient.prompt()
                .system(aiProviderProperties.systemPrompt())
                .user(buildUserPrompt(message.text(), retrievedKnowledge.knowledgeText()))
                .call()
                .content();

        return new ReplyResult(answer, aiProviderProperties.provider(), retrievedKnowledge.citations());
    }

    public boolean vectorStoreReady() {
        return vectorStoreProvider.getIfAvailable() != null;
    }

    private RetrievedKnowledge retrieveKnowledgeIfEnabled(String query) {
        if (!ragProperties.enabled()) {
            return RetrievedKnowledge.empty();
        }

        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            return RetrievedKnowledge.empty();
        }

        List<Document> documents;
        try {
            SearchRequest.Builder builder = SearchRequest.builder()
                    .query(query)
                    .topK(ragProperties.topK());
            if (ragProperties.similarityThreshold() > 0) {
                builder = builder.similarityThreshold(ragProperties.similarityThreshold());
            }
            SearchRequest request = builder.build();
            documents = vectorStore.similaritySearch(request);
        } catch (Exception e) {
            return RetrievedKnowledge.empty();
        }

        if (documents == null || documents.isEmpty()) {
            return RetrievedKnowledge.empty();
        }

        List<String> snippets = new ArrayList<>(documents.size());
        List<String> citations = new ArrayList<>(documents.size());

        for (Document document : documents) {
            if (document == null) {
                continue;
            }
            String content = Objects.toString(document.getText(), "").trim();
            if (content.isEmpty()) {
                continue;
            }

            if (ragProperties.maxCharsPerDoc() > 0 && content.length() > ragProperties.maxCharsPerDoc()) {
                content = content.substring(0, ragProperties.maxCharsPerDoc()) + "...";
            }
            snippets.add(content);

            Object source = document.getMetadata() == null ? null : document.getMetadata().get("source");
            if (source != null) {
                citations.add(String.valueOf(source));
            }
        }

        if (snippets.isEmpty()) {
            return RetrievedKnowledge.empty();
        }

        String knowledgeText = snippets.stream()
                .map(s -> "###\n" + s)
                .collect(Collectors.joining("\n"));

        return new RetrievedKnowledge(knowledgeText, citations);
    }

    private String buildUserPrompt(String question, String knowledge) {
        String knowledgeBlock = knowledge == null || knowledge.isBlank() ? "(none)" : knowledge;
        return """
                User question:
                %s

                Retrieved knowledge:
                %s

                Please answer based on the retrieved knowledge. If it is insufficient, say so and suggest escalating to a human.
                """.formatted(question, knowledgeBlock);
    }

    private record RetrievedKnowledge(String knowledgeText, List<String> citations) {
        private static RetrievedKnowledge empty() {
            return new RetrievedKnowledge("", List.of());
        }
    }
}

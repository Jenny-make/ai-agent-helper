package com.example.customerservice.service;

import com.example.customerservice.config.AiProviderProperties;
import com.example.customerservice.model.CustomerMessage;
import com.example.customerservice.model.ReplyResult;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeAnswerService {

    private final AiProviderProperties aiProviderProperties;
    private final DeepSeekChatModel deepSeekChatModel;
    private final OpenAiChatModel openAiChatModel;
    private final VectorStore vectorStore;

    public KnowledgeAnswerService(
            AiProviderProperties aiProviderProperties,
            DeepSeekChatModel deepSeekChatModel,
            OpenAiChatModel openAiChatModel,
            VectorStore vectorStore
    ) {
        this.aiProviderProperties = aiProviderProperties;
        this.deepSeekChatModel = deepSeekChatModel;
        this.openAiChatModel = openAiChatModel;
        this.vectorStore = vectorStore;
    }

    public ReplyResult answer(CustomerMessage message) {
        var selectedModel = "qwen".equalsIgnoreCase(aiProviderProperties.provider())
                ? openAiChatModel
                : deepSeekChatModel;

        ChatClient chatClient = ChatClient.builder(selectedModel).build();

        String answer = chatClient.prompt()
                .system(aiProviderProperties.systemPrompt())
                .user("""
                        用户问题：
                        %s

                        请结合知识库结果回答。如果知识不足，明确说明并建议转人工。
                        """.formatted(message.text()))
                .call()
                .content();

        return new ReplyResult(answer, aiProviderProperties.provider(), List.of());
    }

    public boolean vectorStoreReady() {
        return vectorStore != null;
    }
}

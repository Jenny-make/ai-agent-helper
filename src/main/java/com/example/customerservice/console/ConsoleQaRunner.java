package com.example.customerservice.console;

import com.example.customerservice.model.CustomerMessage;
import com.example.customerservice.service.KnowledgeAnswerService;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.console.enabled", havingValue = "true")
public class ConsoleQaRunner implements ApplicationRunner {

    private final KnowledgeAnswerService knowledgeAnswerService;

    public ConsoleQaRunner(KnowledgeAnswerService knowledgeAnswerService) {
        this.knowledgeAnswerService = knowledgeAnswerService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        System.out.println("Console Q&A enabled. Type your question and press Enter.");
        System.out.println("Type 'exit' to quit.");

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            while (true) {
                System.out.print("> ");
                String line = reader.readLine();
                if (line == null) {
                    return;
                }

                String question = line.trim();
                if (question.isEmpty()) {
                    continue;
                }
                if ("exit".equalsIgnoreCase(question) || "quit".equalsIgnoreCase(question)) {
                    return;
                }

                var message = new CustomerMessage("console-user", "console-session", question);
                var result = knowledgeAnswerService.answer(message);
                System.out.println(result.answer());
            }
        }
    }
}

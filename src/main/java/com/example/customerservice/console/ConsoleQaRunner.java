package com.example.customerservice.console;

import com.example.customerservice.model.CustomerMessage;
import com.example.customerservice.service.KnowledgeAnswerService;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.console.enabled", havingValue = "true")
public class ConsoleQaRunner implements ApplicationRunner {

    private final KnowledgeAnswerService knowledgeAnswerService;
    private final Environment environment;

    public ConsoleQaRunner(KnowledgeAnswerService knowledgeAnswerService, Environment environment) {
        this.knowledgeAnswerService = knowledgeAnswerService;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        System.out.println("Active profiles: " + Arrays.toString(environment.getActiveProfiles()));
        System.out.println("app.ai.provider: " + environment.getProperty("app.ai.provider"));

        String mmBaseUrl = environment.getProperty("spring.ai.minimax.base-url");
        String mmKey = environment.getProperty("spring.ai.minimax.api-key");
        System.out.println("spring.ai.minimax.base-url: " + mmBaseUrl);
        System.out.println("spring.ai.minimax.api-key set: " + (mmKey != null && !mmKey.isBlank()));

        System.out.println("Console Q&A enabled. Type your question and press Enter.");
        System.out.println("Type 'exit' to quit.");
        int timeoutSeconds = parseIntEnv("APP_CONSOLE_TIMEOUT_SECONDS", 120);
        System.out.println("Timeout: " + timeoutSeconds + "s (env APP_CONSOLE_TIMEOUT_SECONDS)." );

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

                System.out.println("Thinking...");
                System.out.flush();

                try {
                    var future = CompletableFuture.supplyAsync(() -> knowledgeAnswerService.answer(message));
                    var result = future.get(timeoutSeconds, TimeUnit.SECONDS);
                    System.out.println(result.answer());
                } catch (Exception e) {
                    System.out.println("ERROR: " + e.getClass().getSimpleName() + ": " + Objects.toString(e.getMessage(), ""));
                }
            }
        }
    }

    private static int parseIntEnv(String key, int defaultValue) {
        String raw = System.getenv(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}

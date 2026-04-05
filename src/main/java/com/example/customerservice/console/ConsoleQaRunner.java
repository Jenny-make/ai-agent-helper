package com.example.customerservice.console;

import com.example.customerservice.model.CustomerMessage;
import com.example.customerservice.service.ConversationMemoryService;
import com.example.customerservice.service.KnowledgeAnswerService;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.console.enabled", havingValue = "true")
public class ConsoleQaRunner implements ApplicationRunner {

    private final KnowledgeAnswerService knowledgeAnswerService;
    private final ConversationMemoryService conversationMemoryService;
    private final Environment environment;

    public ConsoleQaRunner(
            KnowledgeAnswerService knowledgeAnswerService,
            ConversationMemoryService conversationMemoryService,
            Environment environment
    ) {
        this.knowledgeAnswerService = knowledgeAnswerService;
        this.conversationMemoryService = conversationMemoryService;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        System.out.println("Active profiles: " + Arrays.toString(environment.getActiveProfiles()));
        System.out.println("app.ai.provider: " + environment.getProperty("app.ai.provider"));

        String mmBaseUrl = environment.getProperty("spring.ai.minimax.base-url");
        String mmKey = environment.getProperty("spring.ai.minimax.api-key");
        Charset consoleCharset = resolveConsoleCharset();
        System.out.println("spring.ai.minimax.base-url: " + mmBaseUrl);
        System.out.println("spring.ai.minimax.api-key set: " + (mmKey != null && !mmKey.isBlank()));
        System.out.println("console.charset: " + consoleCharset.displayName());

        System.out.println("Console Q&A enabled. Type your question and press Enter.");
        System.out.println("Type 'exit' to quit.");
        System.out.println("Commands: /new, /history, /session, /help, exit");
        int timeoutSeconds = parseIntEnv("APP_CONSOLE_TIMEOUT_SECONDS", 120);
        System.out.println("Timeout: " + timeoutSeconds + "s (env APP_CONSOLE_TIMEOUT_SECONDS).");
        AtomicInteger sessionCounter = new AtomicInteger(1);
        String sessionId = nextSessionId(sessionCounter);
        System.out.println("Session: " + sessionId);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, consoleCharset))) {
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
                if (question.startsWith("/")) {
                    if ("/new".equalsIgnoreCase(question)) {
                        conversationMemoryService.clearSession(sessionId);
                        sessionId = nextSessionId(sessionCounter);
                        System.out.println("Started new session: " + sessionId);
                    } else if ("/history".equalsIgnoreCase(question)) {
                        System.out.println(conversationMemoryService.formatHistory(sessionId));
                    } else if ("/session".equalsIgnoreCase(question)) {
                        System.out.println("Current session: " + sessionId);
                    } else if ("/help".equalsIgnoreCase(question)) {
                        System.out.println("Commands: /new, /history, /session, /help, exit");
                    } else {
                        System.out.println("Unknown command. Try /help");
                    }
                    continue;
                }

                var message = new CustomerMessage("console-user", sessionId, question);

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

    private static String nextSessionId(AtomicInteger sessionCounter) {
        return "console-session-" + sessionCounter.getAndIncrement();
    }

    private Charset resolveConsoleCharset() {
        String configured = firstNonBlank(
                System.getenv("APP_CONSOLE_CHARSET"),
                environment.getProperty("app.console.charset")
        );
        if (!configured.isBlank()) {
            return Charset.forName(configured.trim());
        }

        if (System.console() != null) {
            return System.console().charset();
        }
        return Charset.defaultCharset();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
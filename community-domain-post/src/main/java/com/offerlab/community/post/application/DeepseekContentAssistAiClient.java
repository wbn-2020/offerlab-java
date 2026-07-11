package com.offerlab.community.post.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
class DeepseekContentAssistAiClient implements ContentAssistAiClient {

    static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 5000;
    static final int DEFAULT_MAX_RESPONSE_BYTES = 1024 * 1024;
    static final int HARD_MAX_RESPONSE_BYTES = 8 * 1024 * 1024;

    private final ObjectMapper objectMapper;
    private volatile HttpClient httpClient;

    @Value("${offerlab.ai.content-assist.enabled:false}")
    private boolean enabled;
    @Value("${offerlab.ai.content-assist.api-key:}")
    private String apiKey = "";
    @Value("${offerlab.ai.content-assist.base-url:https://api.deepseek.com}")
    private String baseUrl = "https://api.deepseek.com";
    @Value("${offerlab.ai.content-assist.model:deepseek-chat}")
    private String model = "deepseek-chat";
    @Value("${offerlab.ai.content-assist.timeout-millis:15000}")
    private int timeoutMillis = 15000;
    @Value("${offerlab.ai.content-assist.connect-timeout-millis:" + DEFAULT_CONNECT_TIMEOUT_MILLIS + "}")
    private int connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT_MILLIS;
    @Value("${offerlab.ai.content-assist.max-response-bytes:" + DEFAULT_MAX_RESPONSE_BYTES + "}")
    private int maxResponseBytes = DEFAULT_MAX_RESPONSE_BYTES;
    @Value("${offerlab.ai.content-assist.allowed-hosts:" + ContentAssistSafety.DEFAULT_ALLOWED_HOSTS + "}")
    private String allowedHosts = ContentAssistSafety.DEFAULT_ALLOWED_HOSTS;
    @Value("${offerlab.ai.content-assist.max-prompt-chars:" + ContentAssistSafety.DEFAULT_MAX_PROMPT_CHARS + "}")
    private int maxPromptChars = ContentAssistSafety.DEFAULT_MAX_PROMPT_CHARS;
    @Value("${offerlab.ai.content-assist.prompt-cost-micros-per-1k:0}")
    private long promptCostMicrosPer1k;
    @Value("${offerlab.ai.content-assist.completion-cost-micros-per-1k:0}")
    private long completionCostMicrosPer1k;

    @Autowired
    DeepseekContentAssistAiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    DeepseekContentAssistAiClient(ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public boolean configured() {
        return ContentAssistSafety.hasText(apiKey);
    }

    @Override
    public Completion complete(ContentAssistScene scene, ContentAssistPrompt prompt) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", scene == ContentAssistScene.QUALITY_SCORE ? 0.1 : 0.3);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt(scene)),
                Map.of("role", "user", "content", userPrompt(scene, prompt))
        ));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(ContentAssistSafety.chatCompletionsUri(baseUrl, allowedHosts))
                .timeout(Duration.ofMillis(timeoutMillis))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        HttpResponse<InputStream> response;
        try {
            response = httpClient().send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            closeQuietly(response.body());
            throw new IllegalStateException("Deepseek HTTP " + response.statusCode());
        }
        String responseBody = readResponseBody(response, effectiveMaxResponseBytes(maxResponseBytes));
        var root = objectMapper.readTree(responseBody);
        var usage = root.path("usage");
        int promptTokens = Math.max(0, usage.path("prompt_tokens").asInt(0));
        int completionTokens = Math.max(0, usage.path("completion_tokens").asInt(0));
        String contentJson = root.path("choices").path(0).path("message").path("content").asText("");
        return new Completion("deepseek", contentJson, promptTokens, completionTokens,
                estimateCostMicros(promptTokens, completionTokens));
    }

    HttpClient httpClient() {
        HttpClient current = httpClient;
        if (current == null) {
            synchronized (this) {
                current = httpClient;
                if (current == null) {
                    current = createHttpClient(connectTimeoutMillis);
                    httpClient = current;
                }
            }
        }
        return current;
    }

    static HttpClient createHttpClient(int configuredConnectTimeoutMillis) {
        int timeout = configuredConnectTimeoutMillis > 0
                ? configuredConnectTimeoutMillis
                : DEFAULT_CONNECT_TIMEOUT_MILLIS;
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeout))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    static int effectiveMaxResponseBytes(int configuredMaxResponseBytes) {
        if (configuredMaxResponseBytes <= 0) {
            return DEFAULT_MAX_RESPONSE_BYTES;
        }
        return Math.min(configuredMaxResponseBytes, HARD_MAX_RESPONSE_BYTES);
    }

    static String readResponseBody(HttpResponse<InputStream> response, int maxBytes) throws IOException {
        long declaredLength;
        try {
            declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Deepseek response has invalid Content-Length", e);
        }
        if (declaredLength > maxBytes) {
            closeQuietly(response.body());
            throw responseTooLarge(maxBytes);
        }

        try (InputStream input = response.body();
             ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 8192))) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read > maxBytes - total) {
                    throw responseTooLarge(maxBytes);
                }
                output.write(buffer, 0, read);
                total += read;
            }
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    private static IllegalStateException responseTooLarge(int maxBytes) {
        return new IllegalStateException("Deepseek response exceeds max-response-bytes=" + maxBytes);
    }

    private static void closeQuietly(InputStream input) {
        if (input == null) {
            return;
        }
        try {
            input.close();
        } catch (IOException ignored) {
            // The size violation is the actionable failure.
        }
    }

    private String systemPrompt(ContentAssistScene scene) {
        return switch (scene) {
            case WRITING -> """
                    You are an editorial copilot for a Chinese developer community.
                    Return strict JSON only with keys:
                    suggestedTitle, summary, outline, suggestions, riskHints.
                    outline/suggestions/riskHints must be string arrays.
                    Keep every string within 80 Chinese characters.
                    """;
            case QUALITY_SCORE -> """
                    You review a draft before publish. This is advisory only and does not replace moderation.
                    Return strict JSON only with keys:
                    score, summary, suggestions, explanations.
                    explanations must be an array of {dimension, score, reason}.
                    score must be an integer between 0 and 100.
                    Keep every string within 80 Chinese characters.
                    """;
            case TAG_TOPIC_SUGGESTIONS -> """
                    Return strict JSON only.
                    """;
        };
    }

    private String userPrompt(ContentAssistScene scene, ContentAssistPrompt prompt) {
        String title = ContentAssistSafety.trimForPrompt(prompt.title(), 255);
        String content = ContentAssistSafety.trimForPrompt(prompt.content(), maxPromptChars);
        String tags = prompt.tagNames() == null ? "" : String.join(", ", prompt.tagNames());
        return switch (scene) {
            case WRITING -> """
                    domain: %s
                    postType: %s
                    title:
                    %s
                    draft:
                    %s
                    tags:
                    %s
                    """.formatted(prompt.domain(), prompt.postType(), title, content, tags);
            case QUALITY_SCORE -> """
                    domain: %s
                    postType: %s
                    title:
                    %s
                    draft:
                    %s
                    tags:
                    %s
                    """.formatted(prompt.domain(), prompt.postType(), title, content, tags);
            case TAG_TOPIC_SUGGESTIONS -> content;
        };
    }

    private long estimateCostMicros(int promptTokens, int completionTokens) {
        long promptCost = (Math.max(0L, promptTokens) * Math.max(0L, promptCostMicrosPer1k)) / 1000L;
        long completionCost = (Math.max(0L, completionTokens) * Math.max(0L, completionCostMicrosPer1k)) / 1000L;
        return promptCost + completionCost;
    }
}

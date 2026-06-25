package com.offerlab.community.post.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
class DeepseekContentAssistAiClient implements ContentAssistAiClient {

    private final ObjectMapper objectMapper;

    @Value("${offerlab.ai.content-assist.enabled:false}")
    private boolean enabled;
    @Value("${offerlab.ai.content-assist.api-key:}")
    private String apiKey;
    @Value("${offerlab.ai.content-assist.base-url:https://api.deepseek.com}")
    private String baseUrl;
    @Value("${offerlab.ai.content-assist.model:deepseek-chat}")
    private String model;
    @Value("${offerlab.ai.content-assist.timeout-millis:15000}")
    private int timeoutMillis;
    @Value("${offerlab.ai.content-assist.allowed-hosts:" + ContentAssistSafety.DEFAULT_ALLOWED_HOSTS + "}")
    private String allowedHosts;
    @Value("${offerlab.ai.content-assist.max-prompt-chars:" + ContentAssistSafety.DEFAULT_MAX_PROMPT_CHARS + "}")
    private int maxPromptChars;
    @Value("${offerlab.ai.content-assist.prompt-cost-micros-per-1k:0}")
    private long promptCostMicrosPer1k;
    @Value("${offerlab.ai.content-assist.completion-cost-micros-per-1k:0}")
    private long completionCostMicrosPer1k;

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
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Deepseek HTTP " + response.statusCode());
        }
        var root = objectMapper.readTree(response.body());
        var usage = root.path("usage");
        int promptTokens = Math.max(0, usage.path("prompt_tokens").asInt(0));
        int completionTokens = Math.max(0, usage.path("completion_tokens").asInt(0));
        String contentJson = root.path("choices").path(0).path("message").path("content").asText("");
        return new Completion("deepseek", contentJson, promptTokens, completionTokens,
                estimateCostMicros(promptTokens, completionTokens));
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

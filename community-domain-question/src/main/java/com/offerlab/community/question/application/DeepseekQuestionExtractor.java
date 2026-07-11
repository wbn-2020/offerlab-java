package com.offerlab.community.question.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.post.api.dto.PostDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeepseekQuestionExtractor implements QuestionExtractor {
    private final RuleBasedQuestionExtractor ruleBasedQuestionExtractor;
    private final ObjectMapper objectMapper;
    private final DeepseekHttpClient deepseekHttpClient;

    @Value("${offerlab.ai.deepseek.enabled:false}")
    private boolean enabled;
    @Value("${offerlab.ai.deepseek.api-key:}")
    private String apiKey;
    @Value("${offerlab.ai.deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;
    @Value("${offerlab.ai.deepseek.model:deepseek-chat}")
    private String model;
    @Value("${offerlab.ai.deepseek.timeout-millis:15000}")
    private int timeoutMillis;
    @Value("${offerlab.ai.deepseek.allowed-hosts:" + DeepseekSafety.DEFAULT_ALLOWED_HOSTS + "}")
    private String allowedHosts;
    @Value("${offerlab.ai.deepseek.extract-max-prompt-chars:" + DeepseekSafety.DEFAULT_MAX_PROMPT_CHARS + "}")
    private int maxPromptChars;
    @Value("${offerlab.ai.deepseek.max-response-bytes:1048576}")
    private int maxResponseBytes;
    @Value("${offerlab.ai.deepseek.max-completion-tokens:2048}")
    private int maxCompletionTokens;
    @Value("${offerlab.ai.deepseek.prompt-cost-micros-per-1k:0}")
    private long promptCostMicrosPer1k;
    @Value("${offerlab.ai.deepseek.completion-cost-micros-per-1k:0}")
    private long completionCostMicrosPer1k;

    @Override
    public List<ExtractedQuestion> extract(PostDTO post) {
        return extractWithMetrics(post).questions();
    }

    @Override
    public QuestionExtractionResult extractWithMetrics(PostDTO post) {
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            return QuestionExtractionResult.rules(ruleBasedQuestionExtractor.extract(post));
        }
        try {
            DeepseekExtraction result = callDeepseek(post);
            if (result.questions().isEmpty()) {
                return fallback(post, "EMPTY_DEEPSEEK_RESULT");
            }
            return new QuestionExtractionResult(
                    result.questions(),
                    "deepseek",
                    false,
                    result.promptTokens(),
                    result.completionTokens(),
                    estimateCostMicros(result.promptTokens(), result.completionTokens()),
                    null
            );
        } catch (Exception e) {
            log.warn("deepseek question extraction failed, fallback to rule extractor: postId={} error={}",
                    post == null ? null : post.getId(), e.getMessage());
            return fallback(post, normalizeErrorCode(e));
        }
    }

    private DeepseekExtraction callDeepseek(PostDTO post) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0.1);
        body.put("max_tokens", Math.max(128, Math.min(maxCompletionTokens, 4096)));
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", """
                        You extract interview questions from Chinese interview-experience posts.
                        Return strict JSON only: {"questions":[{"questionText":"","answerHint":"","examPoint":"","referenceAnswer":"","sourceSnippet":"","qualityReason":"","company":"","position":"","interviewRound":"","difficulty":"easy|medium|hard","confidence":0.0}]}
                        examPoint is the core knowledge or ability being assessed. referenceAnswer should be concise and useful, not longer than 500 Chinese characters. sourceSnippet must quote or summarize the source sentence from the post. qualityReason explains why the question is useful or low confidence.
                        Do not invent company or position when absent. Limit to 20 questions.
                        """),
                Map.of("role", "user", "content", prompt(post))
        ));
        DeepseekHttpClient.Response response = deepseekHttpClient.postJson(
                DeepseekSafety.chatCompletionsUri(baseUrl, allowedHosts),
                timeoutMillis,
                apiKey,
                objectMapper.writeValueAsString(body),
                maxResponseBytes);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Deepseek HTTP " + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode usage = root.path("usage");
        int promptTokens = Math.max(0, usage.path("prompt_tokens").asInt(0));
        int completionTokens = Math.max(0, usage.path("completion_tokens").asInt(0));
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        JsonNode parsed = objectMapper.readTree(content);
        JsonNode questions = parsed.path("questions");
        if (!questions.isArray()) {
            return new DeepseekExtraction(List.of(), promptTokens, completionTokens);
        }
        List<ExtractedQuestion> result = new ArrayList<>();
        for (JsonNode node : questions) {
            String text = node.path("questionText").asText("").trim();
            if (text.length() < 4) {
                continue;
            }
            result.add(ExtractedQuestion.builder()
                    .questionText(text)
                    .answerHint(blankToNull(node.path("answerHint").asText(null)))
                    .examPoint(blankToNull(node.path("examPoint").asText(null)))
                    .referenceAnswer(blankToNull(node.path("referenceAnswer").asText(null)))
                    .sourceSnippet(blankToNull(node.path("sourceSnippet").asText(null)))
                    .qualityReason(blankToNull(node.path("qualityReason").asText(null)))
                    .company(blankToNull(node.path("company").asText(null)))
                    .position(blankToNull(node.path("position").asText(null)))
                    .interviewRound(blankToNull(node.path("interviewRound").asText(null)))
                    .difficulty(normalizeDifficulty(node.path("difficulty").asText("medium")))
                    .confidence(BigDecimal.valueOf(Math.max(0.0, Math.min(1.0, node.path("confidence").asDouble(0.75)))))
                    .build());
            if (result.size() >= 20) {
                break;
            }
        }
        return new DeepseekExtraction(result, promptTokens, completionTokens);
    }

    private QuestionExtractionResult fallback(PostDTO post, String errorCode) {
        return new QuestionExtractionResult(
                ruleBasedQuestionExtractor.extract(post),
                "rules",
                true,
                0,
                0,
                0L,
                errorCode
        );
    }

    private long estimateCostMicros(int promptTokens, int completionTokens) {
        long promptCost = (Math.max(0L, promptTokens) * Math.max(0L, promptCostMicrosPer1k)) / 1000L;
        long completionCost = (Math.max(0L, completionTokens) * Math.max(0L, completionCostMicrosPer1k)) / 1000L;
        return promptCost + completionCost;
    }

    private String normalizeErrorCode(Exception e) {
        String message = e == null ? "" : e.getMessage();
        if (message != null && message.startsWith("Deepseek HTTP ")) {
            return "DEEPSEEK_HTTP_" + message.substring("Deepseek HTTP ".length()).trim();
        }
        if (e instanceof java.net.http.HttpTimeoutException || message != null && message.toLowerCase().contains("timeout")) {
            return "DEEPSEEK_TIMEOUT";
        }
        if (message != null && message.toLowerCase().contains("not allowed")) {
            return "DEEPSEEK_CONFIG_BLOCKED";
        }
        return "DEEPSEEK_EXCEPTION";
    }

    private String prompt(PostDTO post) {
        String ext = DeepseekSafety.trimForPrompt(post == null ? "{}" : post.getExtJson(), 2000);
        String content = DeepseekSafety.trimForPrompt(post == null ? "" : post.getContent(), maxPromptChars);
        String title = DeepseekSafety.trimForPrompt(post == null ? "" : post.getTitle(), 255);
        return "title: " + title + "\nextJson: " + ext + "\ncontent:\n" + content;
    }

    private String normalizeDifficulty(String value) {
        if ("easy".equals(value) || "medium".equals(value) || "hard".equals(value)) {
            return value;
        }
        return "medium";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record DeepseekExtraction(List<ExtractedQuestion> questions, int promptTokens, int completionTokens) {
    }
}

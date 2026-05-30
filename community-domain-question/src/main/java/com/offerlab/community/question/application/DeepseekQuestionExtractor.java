package com.offerlab.community.question.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.post.api.dto.PostDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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

    @Override
    public List<ExtractedQuestion> extract(PostDTO post) {
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            return ruleBasedQuestionExtractor.extract(post);
        }
        try {
            List<ExtractedQuestion> result = callDeepseek(post);
            return result.isEmpty() ? ruleBasedQuestionExtractor.extract(post) : result;
        } catch (Exception e) {
            log.warn("deepseek question extraction failed, fallback to rule extractor: postId={} error={}",
                    post == null ? null : post.getId(), e.getMessage());
            return ruleBasedQuestionExtractor.extract(post);
        }
    }

    private List<ExtractedQuestion> callDeepseek(PostDTO post) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0.1);
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
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(normalizeBaseUrl() + "/chat/completions"))
                .timeout(Duration.ofMillis(timeoutMillis))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Deepseek HTTP " + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        JsonNode parsed = objectMapper.readTree(content);
        JsonNode questions = parsed.path("questions");
        if (!questions.isArray()) {
            return List.of();
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
        return result;
    }

    private String prompt(PostDTO post) {
        String ext = post.getExtJson() == null ? "{}" : post.getExtJson();
        String content = post.getContent() == null ? "" : post.getContent();
        if (content.length() > 8000) {
            content = content.substring(0, 8000);
        }
        return "title: " + post.getTitle() + "\nextJson: " + ext + "\ncontent:\n" + content;
    }

    private String normalizeBaseUrl() {
        String url = baseUrl == null || baseUrl.isBlank() ? "https://api.deepseek.com" : baseUrl.trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
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
}

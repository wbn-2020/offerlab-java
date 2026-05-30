package com.offerlab.community.question.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.question.infrastructure.persistence.po.MockInterviewAnswerPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockInterviewAiReviewService {
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

    public ReviewResult review(MockInterviewAnswerPO answer) {
        if (answer == null || answer.getQuestionId() == null || !hasText(answer.getAnswerText())) {
            return null;
        }
        if (enabled && hasText(apiKey)) {
            try {
                return callDeepseek(answer);
            } catch (Exception e) {
                log.warn("mock interview AI review failed, fallback to rules: sessionId={} questionId={} error={}",
                        answer.getSessionId(), answer.getQuestionId(), e.getMessage());
            }
        }
        return ruleReview(answer);
    }

    private ReviewResult callDeepseek(MockInterviewAnswerPO answer) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0.2);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", """
                        You are a senior Chinese technical interviewer. Review one mock interview answer.
                        Return strict JSON only: {"score":0-5,"completeness":"","projectExpression":"","followUpSuggestion":""}.
                        completeness judges whether the answer covers definition, core steps, tradeoffs, and edge cases.
                        projectExpression judges whether the candidate connects the answer to concrete project scenarios.
                        followUpSuggestion must be one actionable follow-up practice suggestion.
                        Keep each Chinese comment within 80 characters.
                        """),
                Map.of("role", "user", "content", prompt(answer))
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
        return new ReviewResult(
                clampScore(parsed.path("score").asInt(ruleScore(answer))),
                limit(defaultText(parsed.path("completeness").asText(null), ruleCompleteness(answer)), 300),
                limit(defaultText(parsed.path("projectExpression").asText(null), ruleProjectExpression(answer)), 300),
                limit(defaultText(parsed.path("followUpSuggestion").asText(null), ruleFollowUpSuggestion(answer)), 300),
                "deepseek"
        );
    }

    private ReviewResult ruleReview(MockInterviewAnswerPO answer) {
        return new ReviewResult(
                ruleScore(answer),
                ruleCompleteness(answer),
                ruleProjectExpression(answer),
                ruleFollowUpSuggestion(answer),
                "rules"
        );
    }

    private int ruleScore(MockInterviewAnswerPO answer) {
        String text = clean(answer.getAnswerText());
        int score = 1;
        if (text.length() >= 80) score++;
        if (text.length() >= 220) score++;
        if (containsAny(text, "项目", "业务", "线上", "系统", "服务", "场景")) score++;
        if (containsAny(text, "权衡", "优缺点", "边界", "风险", "一致性", "性能", "复杂度")) score++;
        return Math.max(1, Math.min(score, 5));
    }

    private String ruleCompleteness(MockInterviewAnswerPO answer) {
        String text = clean(answer.getAnswerText());
        if (text.length() < 80) {
            return "回答偏短，建议补齐定义、核心步骤、边界条件和方案取舍。";
        }
        if (!containsAny(text, "步骤", "流程", "首先", "然后", "最后", "核心")) {
            return "核心思路已有，但结构还不够清晰，可以按步骤拆开表达。";
        }
        return "回答覆盖了主要思路，下一步重点补边界条件和取舍理由。";
    }

    private String ruleProjectExpression(MockInterviewAnswerPO answer) {
        String text = clean(answer.getAnswerText());
        if (!containsAny(text, "项目", "业务", "线上", "系统", "服务", "场景")) {
            return "项目表达不足，建议加入一个真实业务场景、你的职责和落地结果。";
        }
        if (!containsAny(text, "我", "负责", "落地", "优化", "指标", "结果")) {
            return "已经提到场景，但个人贡献不够明确，建议补充你负责的动作和结果。";
        }
        return "项目关联较清楚，可以继续量化结果并补充遇到的约束。";
    }

    private String ruleFollowUpSuggestion(MockInterviewAnswerPO answer) {
        String text = clean(answer.getAnswerText());
        String question = clean(answer.getQuestionTextSnapshot());
        if (!containsAny(text, "权衡", "优缺点", "边界", "风险")) {
            return "下一轮追问自己：这个方案有什么边界、风险和替代方案。";
        }
        if (!containsAny(text, "项目", "业务", "线上", "系统")) {
            return "下一轮用一个项目例子复述这题，按背景、动作、结果三段讲。";
        }
        return hasText(question) ? "下一轮请准备一个面试官可能继续追问的反例或极端场景。" : "下一轮把答案压缩成 2 分钟版本，保留核心论点。";
    }

    private String prompt(MockInterviewAnswerPO answer) {
        return "question:\n" + limit(clean(answer.getQuestionTextSnapshot()), 1200)
                + "\nreference:\n" + limit(clean(answer.getAnswerHintSnapshot()), 1200)
                + "\nselfReview:\n" + limit(clean(answer.getSelfReview()), 800)
                + "\nanswer:\n" + limit(clean(answer.getAnswerText()), 4000);
    }

    private String normalizeBaseUrl() {
        String url = !hasText(baseUrl) ? "https://api.deepseek.com" : baseUrl.trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private boolean containsAny(String text, String... needles) {
        if (!hasText(text)) return false;
        for (String needle : needles) {
            if (text.contains(needle)) return true;
        }
        return false;
    }

    private String defaultText(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private int clampScore(int score) {
        return Math.max(0, Math.min(score, 5));
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String limit(String value, int max) {
        String safe = value == null ? "" : value.trim();
        return safe.length() <= max ? safe : safe.substring(0, max);
    }

    public record ReviewResult(int score,
                               String completeness,
                               String projectExpression,
                               String followUpSuggestion,
                               String provider) {
    }
}

package com.offerlab.community.question.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.api.dto.TagDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class RuleBasedQuestionExtractor {
    private static final int MAX_QUESTIONS = 20;
    private static final Pattern PREFIX = Pattern.compile("^\\s*(?:[-*•]|\\d+[.)、]|[一二三四五六七八九十]+[、.])\\s*");
    private static final List<String> QUESTION_HINTS = List.of("?", "？", "什么", "如何", "怎么", "为什么", "介绍", "讲一下", "说一下", "区别", "原理");

    private final ObjectMapper objectMapper;

    public List<ExtractedQuestion> extract(PostDTO post) {
        if (post == null || post.getContent() == null || post.getContent().isBlank()) {
            return List.of();
        }
        JsonNode ext = parseExt(post.getExtJson());
        String company = clean(ext.path("company").asText(""));
        String position = clean(ext.path("position").asText(""));
        String round = clean(ext.path("round").asText(""));
        if (round.isBlank()) {
            round = clean(ext.path("interviewRound").asText(""));
        }
        List<Long> tagIds = post.getTags() == null ? List.of() : post.getTags().stream()
                .map(TagDTO::getId)
                .filter(id -> id != null && id > 0)
                .limit(10)
                .toList();

        Set<String> texts = new LinkedHashSet<>();
        for (String rawLine : post.getContent().split("\\R")) {
            String line = PREFIX.matcher(rawLine).replaceFirst("").trim();
            if (looksLikeQuestion(line)) {
                texts.add(trimQuestion(line));
            }
            if (texts.size() >= MAX_QUESTIONS) {
                break;
            }
        }
        if (texts.isEmpty()) {
            String compact = post.getContent().replaceAll("\\s+", " ").trim();
            for (String part : compact.split("[？?]")) {
                String candidate = part.trim();
                if (candidate.length() >= 8 && candidate.length() <= 160) {
                    texts.add(candidate + "？");
                }
                if (texts.size() >= MAX_QUESTIONS) {
                    break;
                }
            }
        }
        List<ExtractedQuestion> result = new ArrayList<>();
        for (String text : texts) {
            result.add(ExtractedQuestion.builder()
                    .questionText(text)
                    .answerHint(answerHint(text))
                    .examPoint(examPoint(text, post))
                    .referenceAnswer(referenceAnswer(text))
                    .sourceSnippet(sourceSnippet(text, post.getContent()))
                    .qualityReason("规则提取：来自面经中的疑问句或面试题描述，建议运营复核后补充更完整答案。")
                    .company(company)
                    .position(position)
                    .interviewRound(round)
                    .difficulty("medium")
                    .confidence(new BigDecimal("0.6200"))
                    .tagIds(tagIds)
                    .build());
        }
        return result;
    }

    private boolean looksLikeQuestion(String value) {
        String text = clean(value);
        if (text.length() < 8 || text.length() > 220) {
            return false;
        }
        return QUESTION_HINTS.stream().anyMatch(text::contains);
    }

    private String trimQuestion(String value) {
        String text = clean(value).replaceAll("\\s+", " ");
        return text.length() > 200 ? text.substring(0, 200) : text;
    }

    private String answerHint(String question) {
        String point = inferPoint(question);
        return "建议围绕「" + point + "」展开：先说明核心概念，再结合项目场景、边界条件和常见追问补充。";
    }

    private String examPoint(String question, PostDTO post) {
        String point = inferPoint(question);
        if (post != null && post.getTags() != null && !post.getTags().isEmpty()) {
            return clean(post.getTags().get(0).getName()) + " / " + point;
        }
        return point;
    }

    private String referenceAnswer(String question) {
        return "可按三段式回答：1. 先给出「" + inferPoint(question) + "」的定义或结论；2. 说明关键机制、优缺点或适用场景；3. 结合自己的项目经验说明如何落地，并主动补充风险点。";
    }

    private String sourceSnippet(String question, String content) {
        String q = clean(question);
        if (q.length() > 160) {
            return q.substring(0, 160);
        }
        String source = clean(content).replaceAll("\\s+", " ");
        if (source.isBlank()) {
            return q;
        }
        int idx = source.indexOf(q);
        if (idx < 0) {
            return q;
        }
        int start = Math.max(0, idx - 40);
        int end = Math.min(source.length(), idx + q.length() + 80);
        return source.substring(start, end);
    }

    private String inferPoint(String question) {
        String text = clean(question).toLowerCase();
        if (text.contains("redis")) return "Redis 数据结构、缓存一致性或高并发设计";
        if (text.contains("mysql") || text.contains("sql") || text.contains("索引")) return "数据库索引、事务或 SQL 优化";
        if (text.contains("spring")) return "Spring 框架机制与工程实践";
        if (text.contains("jvm") || text.contains("gc")) return "JVM 内存模型与性能排查";
        if (text.contains("线程") || text.contains("并发") || text.contains("锁")) return "Java 并发控制与线程安全";
        if (text.contains("mq") || text.contains("kafka") || text.contains("消息")) return "消息队列可靠性与异步解耦";
        if (text.contains("项目") || text.contains("场景")) return "项目经验表达与方案权衡";
        return "基础原理、场景应用与表达完整度";
    }

    private JsonNode parseExt(String extJson) {
        if (extJson == null || extJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(extJson);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}

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

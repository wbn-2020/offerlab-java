package com.offerlab.community.question.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.api.dto.TagDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBasedQuestionExtractorTest {

    private final RuleBasedQuestionExtractor extractor = new RuleBasedQuestionExtractor(new ObjectMapper());

    @Test
    void extractsQuestionsAndCarriesPostMetadata() {
        PostDTO post = PostDTO.builder()
                .id(100L)
                .content("""
                        1. How does Redis persistence work?
                        2. Explain JVM memory model?
                        This line is not a question.
                        """)
                .extJson("{\"company\":\"ByteDance\",\"position\":\"Backend\",\"round\":\"Technical\"}")
                .tags(List.of(TagDTO.builder().id(10L).name("Redis").build()))
                .build();

        List<ExtractedQuestion> result = extractor.extract(post);

        assertFalse(result.isEmpty());
        assertEquals("ByteDance", result.get(0).getCompany());
        assertEquals("Backend", result.get(0).getPosition());
        assertEquals("Technical", result.get(0).getInterviewRound());
        assertEquals(List.of(10L), result.get(0).getTagIds());
    }

    @Test
    void chineseFallbackCopyStaysReadable() {
        PostDTO post = PostDTO.builder()
                .id(101L)
                .content("""
                        一、Redis 如何保证缓存和数据库一致性？
                        二、为什么 Kafka 消息会重复消费？
                        """)
                .tags(List.of(TagDTO.builder().id(11L).name("Java").build()))
                .build();

        List<ExtractedQuestion> result = extractor.extract(post);

        assertEquals(2, result.size());
        String combined = result.stream()
                .map(question -> question.getQuestionText()
                        + question.getAnswerHint()
                        + question.getReferenceAnswer()
                        + question.getQualityReason()
                        + question.getExamPoint())
                .reduce("", String::concat);
        assertTrue(combined.contains("规则提取"));
        assertTrue(combined.contains("建议围绕"));
        assertTrue(combined.contains("可按三段式回答"));
        assertFalse(combined.contains("锛"));
        assertFalse(combined.contains("瑙"));
        assertFalse(combined.contains("鎻"));
        assertFalse(combined.contains("??"));
    }
}

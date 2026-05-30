package com.offerlab.community.post.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.post.domain.model.Post;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostPublishQualityValidatorTest {
    private final PostPublishQualityValidator validator = new PostPublishQualityValidator(new ObjectMapper());

    @Test
    void interviewPostRequiresCompanyPositionEnoughContentAndTwoTags() {
        BizException noMeta = assertThrows(BizException.class, () -> validator.validate(
                Post.TYPE_INTERVIEW,
                "字节 Java 后端一面复盘",
                repeatedContent(130),
                "{}",
                null,
                List.of("Java", "Redis")));
        assertTrue(noMeta.getMessage().contains("公司"));

        BizException shortContent = assertThrows(BizException.class, () -> validator.validate(
                Post.TYPE_INTERVIEW,
                "字节 Java 后端一面复盘",
                "内容太短",
                "{\"company\":\"字节跳动\",\"position\":\"Java 后端\"}",
                null,
                List.of("Java", "Redis")));
        assertTrue(shortContent.getMessage().contains("120"));

        BizException notEnoughTags = assertThrows(BizException.class, () -> validator.validate(
                Post.TYPE_INTERVIEW,
                "字节 Java 后端一面复盘",
                repeatedContent(130),
                "{\"company\":\"字节跳动\",\"position\":\"Java 后端\"}",
                null,
                List.of("Java")));
        assertTrue(notEnoughTags.getMessage().contains("2 个技术标签"));
    }

    @Test
    void validInterviewInputIsTrimmedAndMetadataIsNormalized() {
        PostPublishQualityValidator.ValidatedPostInput input = validator.validate(
                Post.TYPE_INTERVIEW,
                "  字节 Java 后端一面复盘  ",
                "  " + repeatedContent(130) + "  ",
                "{\"company\":\" 字节跳动 \",\"position\":\" Java 后端 \",\"yearsOfExp\":\"3\",\"interviewResult\":\"1\"}",
                List.of(100L),
                List.of(" Redis ", "Redis"));

        assertEquals("字节 Java 后端一面复盘", input.title());
        assertEquals(List.of(100L), input.tagIds());
        assertEquals(List.of("Redis"), input.tagNames());
        assertTrue(input.extJson().contains("\"company\":\"字节跳动\""));
        assertTrue(input.extJson().contains("\"yearsOfExp\":3"));
    }

    @Test
    void nonInterviewPostStillRequiresOneTagAndValidMetadataJson() {
        assertThrows(BizException.class, () -> validator.validate(
                Post.TYPE_BLOG,
                "Spring 事务传播机制总结",
                repeatedContent(60),
                "{bad-json}",
                null,
                List.of("Spring")));

        BizException noTag = assertThrows(BizException.class, () -> validator.validate(
                Post.TYPE_BLOG,
                "Spring 事务传播机制总结",
                repeatedContent(60),
                null,
                null,
                List.of()));
        assertTrue(noTag.getMessage().contains("1 个标签"));
    }

    private String repeatedContent(int length) {
        return "这是一段面经正文，包含问题、回答和复盘。".repeat(length / 18 + 1).substring(0, length);
    }
}

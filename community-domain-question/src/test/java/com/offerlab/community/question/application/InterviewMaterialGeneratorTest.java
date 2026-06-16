package com.offerlab.community.question.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.api.dto.TagDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterviewMaterialGeneratorTest {

    private final InterviewMaterialGenerator generator = new InterviewMaterialGenerator(new ObjectMapper());

    @Test
    void generatesMaterialPackFromProjectReviewWithoutAi() {
        PostDTO post = PostDTO.builder()
                .id(1001L)
                .title("OfferLab Kafka Outbox 稳定性复盘")
                .content("""
                        背景：社区帖子发布后需要通过 Outbox 同步搜索索引和通知。
                        难点：Kafka 临时不可用时，索引任务和通知任务容易堆积。
                        方案：增加失败重试表、状态机、后台预览和批量重放确认。
                        结果：失败任务可追踪，重试耗时降低 35%，排障时间从 30分钟 降到 10分钟。
                        复盘：补充监控、审计链路和防复发检查。
                        """)
                .extJson("{\"company\":\"OfferLab\",\"position\":\"Java 后端\",\"techStacks\":[\"Kafka\",\"MySQL\",\"Spring Boot\"]}")
                .tags(List.of(TagDTO.builder().id(1L).name("Kafka").build()))
                .build();

        InterviewMaterialGenerator.GeneratedMaterial material = generator.generate(post);

        assertTrue(material.starSituation().contains("OfferLab"));
        assertTrue(material.starTask().contains("难点") || material.starTask().contains("目标"));
        assertTrue(material.starAction().contains("方案"));
        assertTrue(material.starResult().contains("35%"));
        assertFalse(material.resumeBullets().isEmpty());
        assertFalse(material.followUpQuestions().isEmpty());
        assertTrue(material.technicalHighlights().stream().anyMatch(item -> item.contains("Kafka")));
    }
}

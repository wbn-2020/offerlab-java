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
        assertTrue(noMeta.getMessage().contains("实体"));

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

    @Test
    void communityPostAcceptsGenericMetadataWithoutCompanyPosition() {
        PostPublishQualityValidator.ValidatedPostInput input = validator.validate(
                Post.TYPE_TECH_ARTICLE,
                "Spring Cloud Gateway 鉴权链路实践",
                repeatedContent(60),
                "{\"difficulty\":\" 进阶 \",\"scenario\":\" 网关鉴权 \",\"contentType\":\"TECH_ARTICLE\","
                        + "\"techStacks\":[\" Spring Boot \",\"Redis\",\"Redis\"],\"summary\":\" 一段摘要 \",\"featured\":\"true\"}",
                null,
                List.of("Spring Cloud"));

        assertEquals(Post.TYPE_TECH_ARTICLE, input.postType());
        assertEquals(List.of("Spring Cloud"), input.tagNames());
        assertTrue(input.extJson().contains("\"difficulty\":\"进阶\""));
        assertTrue(input.extJson().contains("\"scenario\":\"网关鉴权\""));
        assertTrue(input.extJson().contains("\"techStacks\":[\"Spring Boot\",\"Redis\"]"));
        assertTrue(input.extJson().contains("\"featured\":true"));
    }

    @Test
    void communityTypesUseTheirOwnMinimumContentLengths() {
        BizException shortProjectReview = assertThrows(BizException.class, () -> validator.validate(
                Post.TYPE_PROJECT_REVIEW,
                "CodeCoachAI 后端架构复盘",
                repeatedContent(60),
                "{\"scenario\":\"架构复盘\"}",
                null,
                List.of("Java")));
        assertTrue(shortProjectReview.getMessage().contains("80"));

        BizException shortPitfall = assertThrows(BizException.class, () -> validator.validate(
                Post.TYPE_PITFALL,
                "一次 Redis 缓存击穿定位记录",
                repeatedContent(40),
                "{\"scenario\":\"性能排查\"}",
                null,
                List.of("Redis")));
        assertTrue(shortPitfall.getMessage().contains("60"));

        PostPublishQualityValidator.ValidatedPostInput question = validator.validate(
                Post.TYPE_COMMUNITY_QUESTION,
                "MyBatis 分页失效排查问题",
                repeatedContent(35),
                null,
                null,
                List.of("MyBatis"));
        assertEquals(Post.TYPE_COMMUNITY_QUESTION, question.postType());
    }

    @Test
    void systemDesignAndInterviewRecapUseStructuredTemplatesWithoutLegacyMetaRequirement() {
        PostPublishQualityValidator.ValidatedPostInput systemDesign = validator.validate(
                Post.TYPE_SYSTEM_DESIGN,
                "从 0 设计一个消息通知系统",
                """
                        业务目标：支持站内信、邮件和短信多渠道触达。
                        核心场景与约束：消息发送需要可追踪、可重试，并控制第三方通道失败影响。
                        容量估算：日活用户增长后峰值发送量需要预留扩展空间。
                        架构方案：使用消息表、异步任务和通道适配器拆分发送链路。
                        数据模型：记录消息、收件人、通道状态和失败原因。
                        关键取舍：优先保证可靠投递，再逐步优化实时性。
                        风险与演进：补充限流、幂等、死信处理和监控告警。
                        """,
                "{\"contentType\":\"SYSTEM_DESIGN\"}",
                null,
                List.of("架构设计"));
        assertEquals(Post.TYPE_SYSTEM_DESIGN, systemDesign.postType());

        PostPublishQualityValidator.ValidatedPostInput recap = validator.validate(
                Post.TYPE_INTERVIEW_RECAP,
                "某厂 Java 后端二面复盘",
                """
                        面试背景：围绕项目稳定性和缓存治理展开。
                        被问到的问题：缓存击穿、限流降级、事务一致性。
                        追问路径：面试官继续追问压测口径和上线灰度。
                        回答卡点：指标数据不够具体，技术取舍讲得偏泛。
                        可复用 STAR 素材：缓存治理项目可以补充结果数据。
                        后续补强计划：整理压测报告、复盘监控指标和替代方案。
                        """,
                "{\"contentType\":\"INTERVIEW_RECAP\",\"company\":\"某厂\",\"position\":\"Java 后端\"}",
                null,
                List.of("Java"));
        assertEquals(Post.TYPE_INTERVIEW_RECAP, recap.postType());
    }

    @Test
    void projectReviewRequiresStructuredInterviewMaterialSections() {
        BizException missingStructure = assertThrows(BizException.class, () -> validator.validate(
                Post.TYPE_PROJECT_REVIEW,
                "OfferLab 素材包服务端闭环复盘",
                "这是一段很长的项目描述，主要记录模块边界、服务拆分、接口设计和上线过程。".repeat(6),
                "{\"scenario\":\"项目复盘\"}",
                null,
                List.of("Spring Boot")));
        assertTrue(missingStructure.getMessage().contains("项目复盘"));
        assertTrue(missingStructure.getMessage().contains("背景或现象"));
        assertTrue(missingStructure.getMessage().contains("难点或根因"));

        PostPublishQualityValidator.ValidatedPostInput input = validator.validate(
                Post.TYPE_PROJECT_REVIEW,
                "OfferLab 素材包服务端闭环复盘",
                """
                        背景：帖子详情需要把真实工程经历沉淀成面试素材。
                        难点：素材包既要关联帖子版本，也要保证用户只能访问自己的草稿。
                        方案：新增生成、编辑、归档接口，并用规则模板兜底。
                        结果：可在个人知识库按技术栈回看，后续继续补充指标和追问。
                        """,
                "{\"scenario\":\"项目复盘\"}",
                null,
                List.of("Spring Boot"));
        assertEquals(Post.TYPE_PROJECT_REVIEW, input.postType());
    }

    @Test
    void pitfallRequiresRootCauseFixAndResultSections() {
        BizException missingStructure = assertThrows(BizException.class, () -> validator.validate(
                Post.TYPE_PITFALL,
                "一次 Redis 缓存击穿定位记录",
                "现象：接口抖动，影响列表读取。我们补充了大量上下文和排查过程，但暂时还停留在描述阶段，缺少深入分析和后续结论。".repeat(4),
                "{\"scenario\":\"性能排查\"}",
                null,
                List.of("Redis")));
        assertTrue(missingStructure.getMessage().contains("故障复盘"));
        assertTrue(missingStructure.getMessage().contains("难点或根因"));
        assertTrue(missingStructure.getMessage().contains("方案与落地"));

        PostPublishQualityValidator.ValidatedPostInput input = validator.validate(
                Post.TYPE_PITFALL,
                "一次 Redis 缓存击穿定位记录",
                """
                        现象：热点 key 失效后读请求短时间打到数据库。
                        根因：缓存重建没有互斥保护，旧逻辑也缺少降级。
                        修复方案：增加互斥锁、短 TTL 随机抖动和兜底空值缓存。
                        指标结果：峰值数据库读 QPS 降低 70%，错误率恢复到告警线以下。
                        """,
                "{\"scenario\":\"性能排查\"}",
                null,
                List.of("Redis"));
        assertEquals(Post.TYPE_PITFALL, input.postType());
    }

    @Test
    void invalidPostTypeIsRejected() {
        BizException invalidType = assertThrows(BizException.class, () -> validator.validate(
                999,
                "这是一个非法类型测试标题",
                repeatedContent(60),
                null,
                null,
                List.of("Java")));
        assertTrue(invalidType.getMessage().contains("内容类型无效"));
    }

    private String repeatedContent(int length) {
        return "这是一段历史经验正文，包含问题、回答和复盘。".repeat(length / 20 + 1).substring(0, length);
    }

    @Test
    void titleBoundaryUsesTheSharedTwoHundredCharacterContract() {
        String content = repeatedContent(60);
        String title199 = "标".repeat(199);
        String title200 = "标".repeat(200);
        String title201 = "标".repeat(201);

        assertEquals(title199, validator.validate(
                Post.TYPE_TECH_ARTICLE, title199, content, null, null, List.of("Java")).title());
        assertEquals(title200, validator.validate(
                Post.TYPE_TECH_ARTICLE, title200, content, null, null, List.of("Java")).title());

        BizException tooLong = assertThrows(BizException.class, () -> validator.validate(
                Post.TYPE_TECH_ARTICLE, title201, content, null, null, List.of("Java")));
        assertTrue(tooLong.getMessage().contains("8-200"));
        assertTrue(tooLong.getData().toString().contains("title"));
    }

    @Test
    void tagLimitsMatchTheFiveTagEditorContract() {
        String content = repeatedContent(60);
        assertEquals(5, validator.validate(
                Post.TYPE_TECH_ARTICLE,
                "五个标签仍然可以正常发布",
                content,
                null,
                null,
                List.of("Java", "Redis", "MySQL", "Spring", "Kafka")).tagNames().size());

        BizException tooManyNames = assertThrows(BizException.class, () -> validator.validate(
                Post.TYPE_TECH_ARTICLE,
                "六个文本标签必须被拒绝",
                content,
                null,
                null,
                List.of("Java", "Redis", "MySQL", "Spring", "Kafka", "Docker")));
        assertTrue(tooManyNames.getMessage().contains("5 个标签"));

        BizException tooManyIds = assertThrows(BizException.class, () -> validator.validate(
                Post.TYPE_TECH_ARTICLE,
                "六个标签编号必须被拒绝",
                content,
                null,
                List.of(1L, 2L, 3L, 4L, 5L, 6L),
                null));
        assertTrue(tooManyIds.getMessage().contains("5 个标签"));
    }
}

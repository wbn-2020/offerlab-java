package com.offerlab.community.question.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AiTaskDetailGuardTest {
    @Test
    void aiTaskDetailMustExposeSourcePostAndRetryRecords() throws Exception {
        String detailDto = Files.readString(Path.of("src/main/java/com/offerlab/community/question/api/dto/AiTaskDetailDTO.java"), StandardCharsets.UTF_8);
        String facadeApi = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/QuestionFacade.java"), StandardCharsets.UTF_8);
        String facadeImpl = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/QuestionFacadeImpl.java"), StandardCharsets.UTF_8);
        String mapper = Files.readString(Path.of("src/main/java/com/offerlab/community/question/infrastructure/persistence/mapper/AiExtractTaskMapper.java"), StandardCharsets.UTF_8);
        String controller = Files.readString(Path.of("src/main/java/com/offerlab/community/question/controller/QuestionAdminController.java"), StandardCharsets.UTF_8);

        assertTrue(detailDto.contains("private AiTaskDTO task"), "task detail must include the current task");
        assertTrue(detailDto.contains("private String sourcePostTitle"), "task detail must include source post title");
        assertTrue(detailDto.contains("private String sourcePostSummary"), "task detail must include source post summary");
        assertTrue(detailDto.contains("private List<AiTaskDTO> retryRecords"), "task detail must include retry/attempt records");

        assertTrue(facadeApi.contains("AiTaskDetailDTO getTaskDetail(Long taskId)"), "facade must expose task detail contract");
        assertTrue(controller.contains("@GetMapping(\"/ai-tasks/{id}\")"), "admin controller must expose task detail endpoint");
        assertTrue(controller.contains("questionFacade.getTaskDetail(id)"), "controller must delegate task detail lookup");

        assertTrue(mapper.contains("listRecentByPost"), "mapper must query task attempts for the same post");
        assertTrue(mapper.contains("WHERE post_id = #{postId}"), "attempt history must be scoped to the source post");
        assertTrue(mapper.contains("ORDER BY create_time DESC, id DESC"), "attempt history must be deterministic newest first");

        assertTrue(facadeImpl.contains("postFacade.getPost(task.getPostId())"), "detail must try to load visible source post context");
        assertTrue(facadeImpl.contains("postMapper.selectById(task.getPostId())"), "detail must fall back to raw post context for hidden/deleted posts");
        assertTrue(facadeImpl.contains("summaryText(post.getContent(), 180)"), "detail must expose a bounded source content summary");
        assertTrue(facadeImpl.contains("taskMapper.listRecentByPost(task.getPostId(), task.getTaskType(), 8)"), "detail must include recent retry records");
    }

    @Test
    void aiTaskMetricsMustExposeProviderFallbackLatencyTokenAndCost() throws Exception {
        String dto = Files.readString(Path.of("src/main/java/com/offerlab/community/question/api/dto/AiTaskDTO.java"), StandardCharsets.UTF_8);
        String metricsDto = Files.readString(Path.of("src/main/java/com/offerlab/community/question/api/dto/AiTaskMetricsDTO.java"), StandardCharsets.UTF_8);
        String po = Files.readString(Path.of("src/main/java/com/offerlab/community/question/infrastructure/persistence/po/AiExtractTaskPO.java"), StandardCharsets.UTF_8);
        String facadeApi = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/QuestionFacade.java"), StandardCharsets.UTF_8);
        String facadeImpl = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/QuestionFacadeImpl.java"), StandardCharsets.UTF_8);
        String mapper = Files.readString(Path.of("src/main/java/com/offerlab/community/question/infrastructure/persistence/mapper/AiExtractTaskMapper.java"), StandardCharsets.UTF_8);
        String controller = Files.readString(Path.of("src/main/java/com/offerlab/community/question/controller/QuestionAdminController.java"), StandardCharsets.UTF_8);
        String extractor = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/DeepseekQuestionExtractor.java"), StandardCharsets.UTF_8);

        for (String field : List.of("provider", "fallbackUsed", "durationMs", "promptTokens", "completionTokens", "estimatedCostMicros", "errorCode")) {
            assertTrue(po.contains(field), "AI task PO must persist " + field);
            assertTrue(dto.contains(field), "AI task DTO must expose " + field);
        }

        assertTrue(dto.contains("totalTokens"), "AI task DTO must expose total tokens");
        assertTrue(metricsDto.contains("p95DurationMs"), "metrics DTO must expose P95 latency");
        assertTrue(metricsDto.contains("fallbackRate"), "metrics DTO must expose fallback rate");
        assertTrue(metricsDto.contains("providerStats"), "metrics DTO must expose provider buckets");
        assertTrue(metricsDto.contains("errorStats"), "metrics DTO must expose error buckets");
        assertTrue(facadeApi.contains("AiTaskMetricsDTO getTaskMetrics(int limit)"), "facade must expose AI task metrics");
        assertTrue(controller.contains("@GetMapping(\"/ai-tasks/metrics\")"), "admin controller must expose AI task metrics endpoint");
        assertTrue(controller.contains("questionFacade.getTaskMetrics(limit)"), "controller must delegate AI task metrics lookup");
        assertTrue(facadeImpl.contains("extractQuestionsWithMetrics"), "facade must call extractor with metrics");
        assertTrue(facadeImpl.contains("applyExtractionMetrics"), "facade must persist extraction metrics with task status");
        assertTrue(facadeImpl.contains("percentile95"), "facade must compute P95 duration for ops dashboard");
        assertTrue(mapper.contains("provider = NULL"), "retry must clear stale provider metrics");
        assertTrue(mapper.contains("estimated_cost_micros = 0"), "retry must clear stale cost metrics");
        assertTrue(extractor.contains("extractWithMetrics"), "Deepseek extractor must implement metrics extraction");
        assertTrue(extractor.contains("prompt_tokens"), "Deepseek extractor must read prompt token usage");
        assertTrue(extractor.contains("completion_tokens"), "Deepseek extractor must read completion token usage");
        assertTrue(extractor.contains("prompt-cost-micros-per-1k"), "Deepseek extractor must support configurable prompt cost");
    }
}

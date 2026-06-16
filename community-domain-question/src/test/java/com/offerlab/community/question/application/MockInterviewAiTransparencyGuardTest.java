package com.offerlab.community.question.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockInterviewAiTransparencyGuardTest {

    private static final Path ROOT = Path.of(System.getProperty("user.dir")).getParent();

    @Test
    void mockInterviewAiReviewTransparencyMustStayEndToEnd() throws Exception {
        String dto = read(ROOT.resolve("community-domain-question/src/main/java/com/offerlab/community/question/api/dto/MockInterviewAnswerDTO.java"));
        String po = read(ROOT.resolve("community-domain-question/src/main/java/com/offerlab/community/question/infrastructure/persistence/po/MockInterviewAnswerPO.java"));
        String service = read(ROOT.resolve("community-domain-question/src/main/java/com/offerlab/community/question/application/MockInterviewService.java"));
        String aiService = read(ROOT.resolve("community-domain-question/src/main/java/com/offerlab/community/question/application/MockInterviewAiReviewService.java"));
        String taskService = read(ROOT.resolve("community-domain-question/src/main/java/com/offerlab/community/question/application/MockInterviewAiReviewTaskService.java"));
        String mapper = read(ROOT.resolve("community-domain-question/src/main/java/com/offerlab/community/question/infrastructure/persistence/mapper/MockInterviewAnswerMapper.java"));
        String initSql = read(ROOT.resolve("db/init/10_question.sql")).toLowerCase();
        String migration = read(ROOT.resolve("db/migration/20260608_mock_interview_ai_review_transparency.sql")).toLowerCase();

        for (String field : List.of(
                "aiReviewTaskId",
                "aiReviewFallbackUsed",
                "aiReviewDurationMs",
                "aiReviewPromptTokens",
                "aiReviewCompletionTokens",
                "aiReviewEstimatedCostMicros",
                "aiReviewErrorCode"
        )) {
            assertTrue(dto.contains(field), "mock interview answer DTO must expose " + field);
            assertTrue(po.contains(field), "mock interview answer PO must persist " + field);
            assertTrue(service.contains("." + field + "("), "mock interview service must map " + field);
        }

        for (String column : List.of(
                "ai_review_task_id",
                "ai_review_fallback_used",
                "ai_review_duration_ms",
                "ai_review_prompt_tokens",
                "ai_review_completion_tokens",
                "ai_review_estimated_cost_micros",
                "ai_review_error_code"
        )) {
            assertTrue(initSql.contains(column), "init SQL must include " + column);
            assertTrue(migration.contains(column), "migration must add " + column);
            assertTrue(mapper.contains(column), "answer mapper must read/write " + column);
        }

        assertTrue(aiService.contains("promptTokens"), "AI review service must parse prompt token usage");
        assertTrue(aiService.contains("completionTokens"), "AI review service must parse completion token usage");
        assertTrue(aiService.contains("estimatedCostMicros"), "AI review service must estimate configured token cost");
        assertTrue(aiService.contains("fallbackUsed"), "AI review service must distinguish fallback from model success");
        assertTrue(aiService.contains("DEEPSEEK_REVIEW_FAILED"), "AI review service must expose a stable failure code");
        assertTrue(taskService.contains("mock-review-"), "AI review task service must assign a task id");
        assertTrue(taskService.contains("durationMs"), "AI review task service must record task duration");
        assertTrue(service.contains("mockInterviewAiReviewReady()"), "mock interview service must guard writes before transparency columns exist");
        assertTrue(taskService.contains("mockInterviewAiReviewReady()"), "AI review task service must guard writes before transparency columns exist");
        assertTrue(mapper.contains("updateDraftCompat"), "draft writes must have a compatibility SQL path");
        assertTrue(mapper.contains("markPendingForSessionCompat"), "pending-state writes must have a compatibility SQL path");
        assertTrue(mapper.contains("markRetryPendingForSessionCompat"), "retry pending-state writes must have a compatibility SQL path");
        assertTrue(mapper.contains("updateAiReviewCompat"), "AI review success writes must have a compatibility SQL path");
        assertTrue(mapper.contains("updateAiReviewFailedCompat"), "AI review failure writes must have a compatibility SQL path");
        assertFalse(migration.contains("drop table"), "AI review transparency migration must not drop data");
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}

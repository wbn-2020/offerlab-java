package com.offerlab.community.question.application;

import com.offerlab.community.question.controller.QuestionAdminController;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionPerformanceResourceGuardTest {

    @Test
    void batchReviewMustKeepBusinessWritesAndAuditInOneTransaction() throws Exception {
        assertTrue(QuestionAdminController.class
                .getDeclaredMethod("batchReviewQuestions", QuestionAdminController.QuestionBatchReviewRequest.class)
                .isAnnotationPresent(Transactional.class));
    }

    @Test
    void transactionalQuestionWritesMustScheduleIndexingAfterCommit() throws Exception {
        String facade = read("src/main/java/com/offerlab/community/question/application/QuestionFacadeImpl.java");

        assertTrue(facade.contains("scheduleQuestionIndexes"));
        assertTrue(facade.contains("afterCommit.execute(() -> ids.forEach(questionSearchIndexer::requestIndex)"));
        assertTrue(facade.contains("afterCommit.execute(() -> questionSearchIndexer.requestIndex("));
        assertTrue(facade.contains("hideRemovedByIdsAndPostId"));
        assertTrue(facade.contains("scheduleQuestionIndexes(removedQuestionIds"));
        assertFalse(facade.contains("questionSearchIndexer::deleteQuestion"));
        assertTrue(facade.contains("safePageOffset(page, pageSize, MAX_PUBLIC_QUESTION_OFFSET)"));
        assertTrue(facade.contains("safePageOffset(safePage, safePageSize, MAX_ADMIN_QUESTION_OFFSET)"));
    }

    @Test
    void rebuildMustBatchLoadTagsAndAiCallsMustUseBoundedSharedClient() throws Exception {
        String indexer = read("src/main/java/com/offerlab/community/question/application/QuestionSearchIndexer.java");
        String aiReview = read("src/main/java/com/offerlab/community/question/application/MockInterviewAiReviewService.java");
        String extractor = read("src/main/java/com/offerlab/community/question/application/DeepseekQuestionExtractor.java");
        String httpClient = read("src/main/java/com/offerlab/community/question/application/DeepseekHttpClient.java");

        assertTrue(indexer.contains("loadTagsByQuestionIds(rows.stream()"));
        assertTrue(indexer.contains("tagsByQuestion.getOrDefault(row.getId(), List.of())"));
        assertFalse(aiReview.contains("HttpClient.newHttpClient()"));
        assertFalse(extractor.contains("HttpClient.newHttpClient()"));
        assertTrue(aiReview.contains("deepseekHttpClient.postJson("));
        assertTrue(extractor.contains("deepseekHttpClient.postJson("));
        assertTrue(httpClient.contains("private final HttpClient httpClient"));
        assertTrue(httpClient.contains("BodyHandlers.ofInputStream()"));
        assertTrue(httpClient.contains("readNBytes(maxResponseBytes + 1)"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}

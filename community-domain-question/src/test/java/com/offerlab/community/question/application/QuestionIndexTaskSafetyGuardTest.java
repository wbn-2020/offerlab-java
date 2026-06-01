package com.offerlab.community.question.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionIndexTaskSafetyGuardTest {

    @Test
    void questionIndexTasksAndRetriesMustBeDurable() throws Exception {
        String initSql = read("../db/init/10_question.sql").toLowerCase();
        String taskMigration = read("../db/migration/20260601_question_index_task.sql").toLowerCase();
        String retryMigration = read("../db/migration/20260601_question_index_retry_task.sql").toLowerCase();
        String taskService = read("src/main/java/com/offerlab/community/question/application/QuestionIndexTaskService.java");
        String retryService = read("src/main/java/com/offerlab/community/question/application/QuestionIndexRetryService.java");
        String indexer = read("src/main/java/com/offerlab/community/question/application/QuestionSearchIndexer.java");
        String controller = read("src/main/java/com/offerlab/community/question/controller/QuestionAdminController.java");

        assertFalse(taskMigration.contains("drop table"), "question index task migration must be non-destructive");
        assertFalse(retryMigration.contains("drop table"), "question index retry migration must be non-destructive");
        assertTrue(initSql.contains("create table if not exists t_question_index_task"), "fresh DB init must create durable rebuild tasks");
        assertTrue(initSql.contains("create table if not exists t_question_index_retry_task"), "fresh DB init must create durable retry tasks");
        assertTrue(taskService.contains("QuestionIndexTaskMapper"), "rebuild task state must use the database mapper");
        assertFalse(taskService.contains("ConcurrentHashMap"), "rebuild task state must not be in-memory only");
        assertTrue(taskService.contains("STATUS_PENDING"), "rebuild task must expose pending status");
        assertTrue(taskService.contains("STATUS_RUNNING"), "rebuild task must expose running status");
        assertTrue(taskService.contains("STATUS_SUCCEEDED"), "rebuild task must expose succeeded status");
        assertTrue(taskService.contains("STATUS_FAILED"), "rebuild task must expose failed status");
        assertTrue(taskService.contains("retryTask"), "failed rebuild task must be manually retryable");
        assertTrue(controller.contains("/questions/index-tasks/{taskId}/retry"), "admin API must expose rebuild task retry");
        assertTrue(retryService.contains("@Scheduled(fixedDelay = 5000)"), "question ES retry service must replay due tasks");
        assertTrue(retryService.contains("QuestionIndexRetryTaskMapper"), "question ES retries must be persistent");
        assertTrue(indexer.contains("QuestionIndexRetryEvent"), "incremental indexing failures must enqueue retry events");
        assertTrue(indexer.contains("indexQuestionForRetry"), "retry replay must avoid resetting retry counters by re-enqueueing itself");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}

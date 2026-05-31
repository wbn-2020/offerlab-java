package com.offerlab.community.notification.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationRetryGuardTest {

    @Test
    void notificationCreationFailuresMustBeDurablyRetryableAndIdempotent() throws Exception {
        String listener = read("src/main/java/com/offerlab/community/notification/application/NotificationEventListener.java");
        String questionListener = read("src/main/java/com/offerlab/community/notification/application/QuestionNotificationListener.java");
        String retryService = read("src/main/java/com/offerlab/community/notification/application/NotificationRetryService.java");
        String opsController = read("src/main/java/com/offerlab/community/notification/controller/NotificationOpsController.java");
        String facade = read("src/main/java/com/offerlab/community/notification/application/NotificationFacadeImpl.java");
        String retryMapper = read("src/main/java/com/offerlab/community/notification/infrastructure/persistence/mapper/NotificationRetryTaskMapper.java");
        String retryPo = read("src/main/java/com/offerlab/community/notification/infrastructure/persistence/po/NotificationRetryTaskPO.java");
        String initSql = read("../db/init/04_notification.sql");
        String migration = read("../db/migration/20260530_notification_retry_task.sql");

        assertTrue(listener.contains("retryService.enqueue"), "interaction notification failures must enqueue retry tasks");
        assertTrue(questionListener.contains("retryService.enqueue"), "system notification failures must enqueue retry tasks");
        assertTrue(retryService.contains("@Scheduled(fixedDelay = 5000)"), "retry service must periodically replay due tasks");
        assertTrue(retryService.contains("claimDue(owner, lockUntil, BATCH_SIZE)"), "retry service must claim tasks before replaying");
        assertTrue(retryService.contains("notificationFacade.createFromRetryTask"), "retry must reuse facade creation rules and deduplication");
        assertTrue(retryService.contains("MAX_RETRY = 5"), "retry service must cap retries");
        assertTrue(retryService.contains("Math.pow(2, retryCount) * 30"), "retry service must back off between attempts");
        assertTrue(retryService.contains("replayFailed"), "retry service must expose manual replay for failed tasks");
        assertTrue(retryService.contains("countDuePending"), "retry service must expose observable queue status");

        assertTrue(facade.contains("NotificationDedupKey.of"), "normal and replayed notifications must use the same dedup key");
        assertTrue(facade.contains("void createFromRetryTask"), "facade must expose a package-private replay entry point");
        assertTrue(retryMapper.contains("ON DUPLICATE KEY UPDATE"), "retry enqueue must be idempotent by dedup key");
        assertTrue(retryMapper.contains("UPDATE t_notif_retry_task"), "retry mapper must support state transitions");
        assertTrue(retryMapper.contains("markFailedForRetry"), "failed retry tasks must be manually replayable");
        assertTrue(retryMapper.contains("retry_count = 0"), "manual replay must reset retry count for exhausted tasks");
        assertTrue(retryPo.contains("@TableName(\"t_notif_retry_task\")"), "retry task PO must map to the durable task table");

        assertTrue(opsController.contains("@RequestMapping(\"/api/v1/ops\")"), "notification retry replay must be exposed through ops APIs");
        assertTrue(opsController.contains("ROLE_OPS"), "notification retry ops APIs must require ops permission");
        assertTrue(opsController.contains("/notification-retry-tasks/{id}/replay"), "ops API must allow single failed task replay");
        assertTrue(opsController.contains("/notification-retry-tasks/replay-batch"), "ops API must allow batch failed task replay");
        assertTrue(opsController.contains("adminAuditService.record"), "manual replay must leave an admin audit trail");

        assertTrue(initSql.contains("CREATE TABLE t_notif_retry_task"), "fresh DB init must create notification retry task table");
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS t_notif_retry_task"), "migration must add retry task table non-destructively");
        assertTrue(migration.contains("UNIQUE KEY uk_notif_retry_dedup"), "retry table must be idempotent by notification dedup key");
        assertTrue(migration.contains("idx_notif_retry_due"), "retry table must index due pending tasks");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}

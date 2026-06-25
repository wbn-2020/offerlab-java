package com.offerlab.community.user.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserTaskStateSafetyGuardTest {

    @Test
    void userTaskStateMustBeDurableAndWiredIntoSchemaChecks() throws Exception {
        String initSql = read("../db/init/01_user.sql").toLowerCase();
        String migration = read("../db/migration/20260623_user_task_state.sql").toLowerCase();
        String service = read("src/main/java/com/offerlab/community/user/application/UserTaskApplicationService.java");
        String listener = read("src/main/java/com/offerlab/community/user/application/UserTaskEventListener.java");
        String controller = read("src/main/java/com/offerlab/community/user/controller/UserTaskController.java");
        String migrationCheck = read("../community-infrastructure/src/main/java/com/offerlab/community/infra/db/MigrationCheckService.java");
        String schemaGuard = read("../scripts/check-schema-readiness.mjs").toLowerCase();

        assertFalse(migration.contains("drop table"), "user task state migration must be non-destructive");
        assertTrue(initSql.contains("create table if not exists t_user_task_state"), "fresh DB init must create user task state table");
        assertTrue(service.contains("UserTaskStateMapper"), "task state must use the database mapper");
        assertFalse(service.contains("ConcurrentHashMap"), "task state must not be in-memory only");
        assertTrue(service.contains("VIEW_PUBLIC_CONTENT"), "onboarding browse task must exist");
        assertTrue(service.contains("FOLLOW_FIRST_USER"), "onboarding follow task must exist");
        assertTrue(service.contains("INTERACT_ONCE"), "onboarding interaction task must exist");
        assertTrue(service.contains("PUBLISH_FIRST_POST"), "onboarding publish task must exist");
        assertTrue(service.contains("DAILY_VIEW_PUBLIC_CONTENT"), "daily browse task must exist");
        assertTrue(service.contains("DAILY_INTERACT_ONCE"), "daily interaction task must exist");
        assertTrue(service.contains("DAILY_PUBLISH_ONCE"), "daily publish task must exist");
        assertTrue(listener.contains("@TransactionalEventListener"), "task auto-completion must hook into trusted business events");
        assertTrue(controller.contains("/api/v1/me"), "task APIs must live under /api/v1/me");
        assertTrue(controller.contains("/onboarding-tasks"), "onboarding task endpoints must exist");
        assertTrue(controller.contains("/daily-tasks"), "daily task endpoints must exist");
        assertTrue(migrationCheck.contains("t_user_task_state"), "migration readiness must include task state table");
        assertTrue(schemaGuard.contains("t_user_task_state"), "schema readiness script must include task state table");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}

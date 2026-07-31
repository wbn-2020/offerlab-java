package com.offerlab.community.post.collaboration.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentMaintenanceReassignGuardTest {

    @Test
    void reassignRequiresExplicitReplacementAndKeepsSubmittedForReview() throws Exception {
        String controller = read(
                "src/main/java/com/offerlab/community/post/collaboration/controller/"
                        + "ContentMaintenanceTaskController.java");
        String service = read(
                "src/main/java/com/offerlab/community/post/collaboration/application/"
                        + "ContentMaintenanceTaskService.java");
        String mapper = read(
                "src/main/java/com/offerlab/community/post/collaboration/infrastructure/persistence/"
                        + "ContentMaintenanceTaskMapper.java");
        String command = read(
                "src/main/java/com/offerlab/community/post/collaboration/api/"
                        + "ContentMaintenanceTaskReassignCmd.java");
        int reassignSqlStart = mapper.indexOf("SET assignee_uid = #{replacementUid}");
        int reassignMethodEnd = mapper.indexOf("int reassign", reassignSqlStart);
        String reassignSql = mapper.substring(reassignSqlStart, reassignMethodEnd);

        assertTrue(controller.contains("/{taskId}/reassign"));
        assertTrue(command.contains("replacementUid"));
        assertTrue(service.contains("CHANNEL_RESOURCE_MAINTAINER"));
        assertTrue(service.contains("Set.of(\"OPEN\", \"CLAIMED\")"));
        assertTrue(service.contains("CONTENT_MAINTENANCE_TASK_REASSIGN"));
        assertTrue(reassignSql.contains("task_status IN ('OPEN', 'CLAIMED')"));
        assertFalse(reassignSql.contains("SUBMITTED"));
        assertFalse(service.toLowerCase().contains("findreplacement"));
        assertFalse(service.toLowerCase().contains("batchreassign"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}

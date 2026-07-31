package com.offerlab.community.post.collaboration.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentMaintenanceCandidateGuardTest {

    @Test
    void candidatesAreRoleScopedPublicAndReadOnly() throws Exception {
        String controller = read(
                "src/main/java/com/offerlab/community/post/collaboration/controller/ContentMaintenanceTaskController.java");
        String service = read(
                "src/main/java/com/offerlab/community/post/collaboration/application/ContentMaintenanceTaskService.java");
        String mapper = read(
                "src/main/java/com/offerlab/community/post/collaboration/infrastructure/persistence/ContentMaintenanceTaskMapper.java");
        String dto = read(
                "src/main/java/com/offerlab/community/post/collaboration/api/ContentMaintenanceCandidateDTO.java");

        assertTrue(controller.contains("@GetMapping(\"/candidates\")"));
        assertTrue(controller.contains("@RateLimit"));
        assertTrue(controller.contains("UserContext.require()"));
        assertTrue(service.contains("CHANNEL_RESOURCE_MAINTAINER"));
        assertTrue(service.contains("hasActiveGrant"));
        assertTrue(mapper.contains("task.task_status = 'OPEN'"));
        assertTrue(mapper.contains("source_post.is_deleted = 0"));
        assertTrue(mapper.contains("source_post.post_status = 1"));
        assertTrue(mapper.contains("source_post.visibility = 1"));
        assertFalse(dto.contains("detail"));
        assertFalse(dto.contains("reviewNote"));
        assertFalse(dto.contains("deliveryNote"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}

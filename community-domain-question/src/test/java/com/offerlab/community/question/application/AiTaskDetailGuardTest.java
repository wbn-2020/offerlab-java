package com.offerlab.community.question.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
}

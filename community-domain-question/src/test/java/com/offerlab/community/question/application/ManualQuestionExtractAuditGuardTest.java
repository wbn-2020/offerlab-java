package com.offerlab.community.question.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualQuestionExtractAuditGuardTest {

    @Test
    void manualQuestionExtractionMustValidatePostIdAndWriteAuditLog() throws Exception {
        String controller = read("src/main/java/com/offerlab/community/question/controller/QuestionAdminController.java");
        String facade = read("src/main/java/com/offerlab/community/question/application/QuestionFacadeImpl.java");

        assertTrue(controller.contains("@PathVariable @Positive Long postId"),
                "manual extract endpoint must reject zero or negative post ids before side effects");
        assertTrue(controller.contains("if (postId == null || postId <= 0)"),
                "manual extract endpoint must guard invalid post ids even in standalone controller tests");
        assertTrue(controller.contains("POST_QUESTION_EXTRACT_TASK"),
                "manual extract endpoint must write a distinct admin audit action");
        assertTrue(controller.contains("adminAuditService.requireWritable(\"POST_QUESTION_EXTRACT_TASK\", \"AI_TASK\", null)"),
                "manual extract endpoint must fail closed before side effects when audit is unavailable");
        assertTrue(controller.contains("adminAuditService.recordRequired(uid, \"POST_QUESTION_EXTRACT_TASK\", \"AI_TASK\", taskId"),
                "manual extract audit should be linked to the created AI task");
        assertTrue(controller.contains("Map.of(\"postId\", postId, \"manual\", true)"),
                "manual extract audit must include post id and manual trigger context");

        assertTrue(facade.contains("if (postId == null || postId <= 0)"),
                "question facade must defend against non-controller callers with invalid post ids");
        assertTrue(facade.contains("throw new BizException(ErrorCode.PARAM_ERROR)"),
                "invalid manual extract input must fail with a parameter error");
        assertTrue(facade.indexOf("if (postId == null || postId <= 0)") < facade.indexOf("taskMapper.insert(task)"),
                "post id validation must happen before task insertion");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}

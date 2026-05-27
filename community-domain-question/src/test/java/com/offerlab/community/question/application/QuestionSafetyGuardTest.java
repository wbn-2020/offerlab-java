package com.offerlab.community.question.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionSafetyGuardTest {

    @Test
    void questionInitSqlMustNotDropTables() throws Exception {
        String sql = Files.readString(Path.of("../db/init/10_question.sql"), StandardCharsets.UTF_8).toLowerCase();

        assertFalse(sql.contains("drop table"), "question init SQL must be migration-safe and never drop existing data");
        assertTrue(sql.contains("create table if not exists t_user_prep_target"));
    }

    @Test
    void facadeMustHideQuestionsWhenSourcePostBecomesInvisible() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/QuestionFacadeImpl.java"), StandardCharsets.UTF_8);

        assertTrue(source.contains("rawPost != null"), "invisible existing posts must be distinguished from missing posts");
        assertTrue(source.contains("hidePostQuestions(task.getPostId())"), "invisible source posts must hide extracted questions");
    }

    @Test
    void questionDetailMustNotUseSharedCacheForViewerProgress() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/QuestionFacadeImpl.java"), StandardCharsets.UTF_8);

        assertFalse(source.contains("MultiLevelCache<QuestionDetailDTO>"), "viewer-specific favorite/progress state must not be cached globally");
    }
}

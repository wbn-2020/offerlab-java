package com.offerlab.community.question.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterviewMaterialSafetyGuardTest {

    @Test
    void materialPackStorageMustBeNonDestructiveAndIndexed() throws Exception {
        String initSql = read("../db/init/10_question.sql").toLowerCase();
        String migrationSql = read("../db/migration/20260615_interview_material_pack.sql").toLowerCase();

        assertFalse(migrationSql.contains("drop table"), "interview material migration must never drop existing data");
        assertTrue(initSql.contains("create table if not exists t_interview_material_pack"),
                "fresh DB init must create interview material packs");
        assertTrue(migrationSql.contains("create table if not exists t_interview_material_pack"),
                "existing DB migration must create interview material packs non-destructively");
        assertTrue(initSql.contains("unique key uk_uid_post"), "one user should keep one editable pack per source post");
        assertTrue(initSql.contains("idx_uid_saved_time"), "prep workbench queries need a uid/saved/update index");
        assertTrue(initSql.contains("resume_bullet_json"), "resume bullets must be stored separately");
        assertTrue(initSql.contains("follow_up_question_json"), "follow-up questions must be stored separately");
        assertTrue(initSql.contains("technical_highlight_json"), "technical highlights must be stored separately");
        assertTrue(initSql.contains("missing_hint_json"), "material gap hints must be stored separately");
    }

    @Test
    void knowledgeWorkbenchFiltersMustReachMapperSql() throws Exception {
        String controller = read("src/main/java/com/offerlab/community/question/controller/InterviewMaterialController.java");
        String service = read("src/main/java/com/offerlab/community/question/application/InterviewMaterialService.java");
        String mapper = read("src/main/java/com/offerlab/community/question/infrastructure/persistence/mapper/InterviewMaterialPackMapper.java");
        String dto = read("src/main/java/com/offerlab/community/question/api/dto/UserKnowledgeDTO.java");
        String updateCmd = read("src/main/java/com/offerlab/community/question/api/dto/InterviewMaterialUpdateCmd.java");

        assertTrue(controller.contains("@GetMapping(\"/me/knowledge\")"), "knowledge API endpoint must stay exposed");
        assertTrue(controller.contains("@Size(max = 64) String interviewRound"),
                "knowledge API must validate interview round filter length");
        assertTrue(service.contains("techStackFilter, roundFilter, postType, savedOnly, safeLimit"),
                "service must pass cleaned interview round filter into mapper");
        assertTrue(mapper.contains("@Param(\"interviewRound\") String interviewRound"),
                "mapper method must accept interview round filter");
        assertTrue(mapper.contains("JSON_EXTRACT(e.ext_json, '$.round')"),
                "mapper SQL must support legacy round metadata");
        assertTrue(mapper.contains("JSON_EXTRACT(e.ext_json, '$.interviewRound')"),
                "mapper SQL must support interviewRound metadata");
        assertTrue(mapper.contains("JSON_EXTRACT(e.ext_json, '$.interviewRounds')"),
                "mapper SQL must support interviewRounds metadata");
        assertTrue(mapper.contains("selectFavoritePostIdsFiltered"),
                "favorite posts must use the same knowledge workbench filters");
        assertTrue(mapper.contains("selectFavoriteQuestionIdsFiltered"),
                "favorite questions must use the same knowledge workbench filters");
        assertTrue(mapper.contains("countFavoritePostsFiltered"),
                "favorite post counts must match filtered visible post queries");
        assertTrue(mapper.contains("countFavoriteQuestionsFiltered"),
                "favorite question counts must match filtered visible question queries");
        assertTrue(mapper.contains("AND p.post_status = 1"),
                "knowledge queries must exclude draft or taken-down source posts");
        assertTrue(mapper.contains("AND p.visibility = 1"),
                "favorite knowledge queries must exclude private source posts");
        assertTrue(service.contains("questionFacade.getVisibleQuestionsByIds"),
                "knowledge service must return filtered favorite question previews");
        assertTrue(dto.contains("materialGapHints"), "knowledge workbench must expose material gap hints");
        assertTrue(dto.contains("weakTags"), "knowledge workbench must expose weak tag summaries");
        assertTrue(updateCmd.contains("@Size(max = 12)"),
                "material edit payload lists must be bounded before service normalization");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}

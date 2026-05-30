package com.offerlab.community.question.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockInterviewSafetyGuardTest {

    @Test
    void mockInterviewSqlMustBeNonDestructiveAndUserScoped() throws Exception {
        String initSql = Files.readString(Path.of("../db/init/10_question.sql"), StandardCharsets.UTF_8).toLowerCase();
        String migrationSql = Files.readString(Path.of("../db/migration/20260528_mock_interview.sql"), StandardCharsets.UTF_8).toLowerCase();
        String snapshotMigrationSql = Files.readString(Path.of("../db/migration/20260528_mock_interview_snapshot.sql"), StandardCharsets.UTF_8).toLowerCase();

        assertFalse(migrationSql.contains("drop table"), "mock interview migration must not drop tables");
        assertFalse(snapshotMigrationSql.contains("drop table"), "mock interview snapshot migration must not drop tables");
        assertTrue(initSql.contains("create table if not exists t_mock_interview_session"));
        assertTrue(initSql.contains("create table if not exists t_mock_interview_answer"));
        assertTrue(migrationSql.contains("create table if not exists t_mock_interview_session"));
        assertTrue(migrationSql.contains("create table if not exists t_mock_interview_answer"));
        assertTrue(initSql.contains("focus_tag"), "mock interview sessions must keep the focused weak tag");
        assertTrue(initSql.contains("question_text_snapshot"), "mock answers must keep a question text snapshot");
        assertTrue(snapshotMigrationSql.contains("question_text_snapshot"), "existing databases must receive mock answer snapshots non-destructively");
        assertTrue(snapshotMigrationSql.contains("focus_tag"), "existing databases must receive focused tag support non-destructively");
        assertTrue(initSql.contains("uid              bigint"), "sessions and answers must remain user scoped");
        assertTrue(initSql.contains("idx_uid_update_time"), "recent mock interview queries need a uid/update_time index");
        assertTrue(initSql.contains("unique key uk_session_question"), "a session should keep one answer per question");
        assertTrue(initSql.contains("ai_reviewed"), "mock answers must persist whether AI/rule review was generated");
        assertTrue(initSql.contains("ai_score"), "mock answers must persist AI/rule score separately from self score");
        assertTrue(initSql.contains("ai_completeness"), "mock answers must persist completeness feedback");
        assertTrue(initSql.contains("ai_project_expression"), "mock answers must persist project-expression feedback");
        assertTrue(initSql.contains("ai_follow_up_suggestion"), "mock answers must persist follow-up suggestions");
        String aiReviewMigrationSql = Files.readString(Path.of("../db/migration/20260530_mock_interview_ai_review.sql"), StandardCharsets.UTF_8).toLowerCase();
        assertFalse(aiReviewMigrationSql.contains("drop table"), "mock interview AI review migration must not drop tables");
        assertTrue(aiReviewMigrationSql.contains("add column ai_reviewed"), "existing databases must receive AI review flag non-destructively");
        assertTrue(aiReviewMigrationSql.contains("add column ai_follow_up_suggestion"), "existing databases must receive follow-up suggestion non-destructively");
    }

    @Test
    void mockInterviewApiMustRequireLoginAndSaveAnswers() throws Exception {
        String controller = Files.readString(Path.of("src/main/java/com/offerlab/community/question/controller/MockInterviewController.java"), StandardCharsets.UTF_8);
        String service = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/MockInterviewService.java"), StandardCharsets.UTF_8);
        String normalizedService = service.replace("\r\n", "\n");
        String questionMapper = Files.readString(Path.of("src/main/java/com/offerlab/community/question/infrastructure/persistence/mapper/InterviewQuestionMapper.java"), StandardCharsets.UTF_8);
        String answerMapper = Files.readString(Path.of("src/main/java/com/offerlab/community/question/infrastructure/persistence/mapper/MockInterviewAnswerMapper.java"), StandardCharsets.UTF_8);
        String sessionMapper = Files.readString(Path.of("src/main/java/com/offerlab/community/question/infrastructure/persistence/mapper/MockInterviewSessionMapper.java"), StandardCharsets.UTF_8);
        String statsDto = Files.readString(Path.of("src/main/java/com/offerlab/community/question/api/dto/MockInterviewStatsDTO.java"), StandardCharsets.UTF_8);
        String submitCmd = Files.readString(Path.of("src/main/java/com/offerlab/community/question/api/dto/MockInterviewSubmitCmd.java"), StandardCharsets.UTF_8);
        String draftCmd = Files.readString(Path.of("src/main/java/com/offerlab/community/question/api/dto/MockInterviewDraftCmd.java"), StandardCharsets.UTF_8);
        String answerDto = Files.readString(Path.of("src/main/java/com/offerlab/community/question/api/dto/MockInterviewAnswerDTO.java"), StandardCharsets.UTF_8);
        String answerPo = Files.readString(Path.of("src/main/java/com/offerlab/community/question/infrastructure/persistence/po/MockInterviewAnswerPO.java"), StandardCharsets.UTF_8);
        String aiReviewService = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/MockInterviewAiReviewService.java"), StandardCharsets.UTF_8);

        assertTrue(controller.contains("UserContext.require()"), "mock interview endpoints must require login");
        assertTrue(controller.contains("@GetMapping(\"/stats\")"), "mock interview should expose a personal stats endpoint");
        assertTrue(controller.contains("@PutMapping(\"/{id}/draft\")"), "mock interview should expose a server-side draft save endpoint");
        assertTrue(controller.contains("@RateLimit"), "mock interview mutations should be rate limited");
        assertTrue(service.contains("@Transactional"), "start and submit must persist session and answers atomically");
        assertTrue(service.contains("selectByUser(sessionId, uid)"), "submit/detail must enforce session ownership");
        assertTrue(service.contains("saveDraft(Long uid, Long sessionId, MockInterviewDraftCmd cmd)"), "mock interview drafts must be saved through an explicit service method");
        assertTrue(service.contains("sessionMapper.updateDraft(sessionId, uid, answered, totalScore, duration)"), "draft save must update started-session summary without completing it");
        assertTrue(service.contains("STATUS_COMPLETED.equals(session.getStatus())"), "completed mock interviews should not be overwritten by repeated submit");
        assertTrue(service.contains("sessionMapper.complete(sessionId, uid, answered, totalScore, duration, STATUS_COMPLETED) == 0"), "submit must use a CAS complete update and reject concurrent repeated submits");
        assertFalse(service.contains("answerMapper.deleteBySession"), "submit must not delete and recreate existing answer rows");
        assertTrue(service.contains("toSessionDtos(uid, sessionMapper.selectRecentByUser(uid, safeLimit))"), "recent mock interviews should batch-load answers instead of querying per session");
        assertTrue(service.contains("insightsBy(completedSessions, MockInterviewSessionDTO::getFocusTag)"), "stats should include focus-tag performance insight");
        assertTrue(service.contains("insightsBy(completedSessions, MockInterviewSessionDTO::getCompany)"), "stats should include company performance insight");
        assertTrue(service.contains("insightsBy(completedSessions, MockInterviewSessionDTO::getPosition)"), "stats should include position performance insight");
        assertTrue(service.contains("INSIGHT_WINDOW_SIZE"), "stats insight window must be an explicit contract");
        assertTrue(service.contains(".insightWindowSize(INSIGHT_WINDOW_SIZE)"), "stats DTO must disclose the recent insight window");
        assertTrue(service.contains(".weakAnswers(weakAnswers(completedSessions))"), "stats should include recent weak answers from the insight window");
        assertTrue(service.contains("WEAK_ANSWER_LIMIT"), "stats weak answers must have an explicit display limit");
        assertTrue(service.contains(".filter(this::isWeakAnswer)"), "stats weak answers must reuse a dedicated weak-answer rule");
        assertTrue(service.contains("fillQuestionSnapshot(answer, question)"), "mock interview answers must snapshot question text for historical reports");
        assertTrue(service.contains("toQuestionDto(questions.get(answer.getQuestionId()), answer"), "mock interview history must fall back to answer snapshots when questions disappear");
        assertTrue(service.contains("tagsByQuestion = tagsByQuestionIds(questionIds)"), "mock interview question tags should be batch loaded for a session");
        assertFalse(service.contains("tagsByQuestionIds(List.of(row.getId()))"), "mock interview answer DTO assembly must not query tags once per question");
        assertTrue(service.contains("cmd.getFocusTag()"), "mock interview start should accept a focused weak tag");
        assertTrue(service.contains("selectMockInterviewQuestionsByTag"), "mock interview should support tag-focused question selection");
        assertTrue(service.contains("answerMapper.updateDraft(uid, sessionId, old.getQuestionId(), answerText, selfReview, score)"), "submit must persist user answers by updating existing answer rows");
        assertTrue(submitCmd.contains("private Boolean aiReviewEnabled"), "mock interview submit must allow users to turn optional AI review on or off");
        assertTrue(service.contains("Boolean.TRUE.equals(cmd == null ? null : cmd.getAiReviewEnabled())"), "submit must only generate AI review when explicitly enabled");
        assertTrue(service.contains("reviewAnswers(uid, sessionId, existing)"), "submit must review answers after the session is completed");
        assertTrue(service.contains("aiReviewService.review(answer)"), "mock interview service must delegate AI/rule feedback generation");
        assertTrue(answerMapper.contains("updateAiReview"), "answer mapper must persist AI/rule review fields");
        assertTrue(answerMapper.contains("ai_completeness"), "answer mapper must persist completeness feedback");
        assertTrue(answerMapper.contains("ai_project_expression"), "answer mapper must persist project-expression feedback");
        assertTrue(answerMapper.contains("ai_follow_up_suggestion"), "answer mapper must persist follow-up suggestion feedback");
        assertTrue(answerDto.contains("aiReviewed"), "answer DTO must expose AI/rule review state");
        assertTrue(answerDto.contains("aiScore"), "answer DTO must expose AI/rule score");
        assertTrue(answerDto.contains("aiCompleteness"), "answer DTO must expose completeness feedback");
        assertTrue(answerPo.contains("aiReviewProvider"), "answer PO must persist the feedback provider");
        assertTrue(aiReviewService.contains("offerlab.ai.deepseek.enabled"), "AI review should reuse the existing DeepSeek switch");
        assertTrue(aiReviewService.contains("callDeepseek"), "AI review should call DeepSeek when configured");
        assertTrue(aiReviewService.contains("return ruleReview(answer)"), "AI review must fall back to deterministic rules");
        assertTrue(aiReviewService.contains("completeness"), "AI review must include completeness feedback");
        assertTrue(aiReviewService.contains("projectExpression"), "AI review must include project expression feedback");
        assertTrue(aiReviewService.contains("followUpSuggestion"), "AI review must include follow-up suggestions");
        assertFalse(normalizedService.contains("}\n            totalScore += score;"), "mock interview score summaries must not count unanswered questions");
        assertTrue(countOccurrences(normalizedService, "answered++;\n                totalScore += score;") >= 2, "mock interview submit and draft summaries must count score only inside the answered branch");
        assertTrue(answerMapper.contains("selectBySessions"), "mock interview list and stats should batch load answers by session ids");
        assertTrue(answerMapper.contains("int updateDraft"), "draft save must update existing answer rows instead of recreating snapshots");
        assertFalse(answerMapper.contains("deleteBySession"), "mock interview answers should not be deleted during submit");
        assertTrue(answerMapper.contains("AND EXISTS"), "draft answer updates must be guarded by the started session state");
        assertTrue(sessionMapper.contains("AND status = 'started'"), "draft session updates must not complete or overwrite completed sessions");
        assertTrue(submitCmd.contains("@NotNull") && submitCmd.contains("@Positive"), "submit answer question ids must be validated");
        assertTrue(draftCmd.contains("@NotNull") && draftCmd.contains("@Positive"), "draft answer question ids must be validated");
        assertTrue(statsDto.contains("focusTagInsights"), "stats DTO must expose focus-tag insight");
        assertTrue(statsDto.contains("companyInsights"), "stats DTO must expose company insight");
        assertTrue(statsDto.contains("positionInsights"), "stats DTO must expose position insight");
        assertTrue(statsDto.contains("insightWindowSize"), "stats DTO must expose the insight window size");
        assertTrue(statsDto.contains("weakAnswers"), "stats DTO must expose recent weak answers");
        assertTrue(questionMapper.contains("selectMockInterviewQuestions"), "mock interview should select questions from the approved question bank");
        assertTrue(questionMapper.contains("selectMockInterviewQuestionsByTag"), "mock interview should select focused questions by tag");
    }

    private static int countOccurrences(String source, String needle) {
        int count = 0;
        int index = source.indexOf(needle);
        while (index >= 0) {
            count++;
            index = source.indexOf(needle, index + needle.length());
        }
        return count;
    }
}

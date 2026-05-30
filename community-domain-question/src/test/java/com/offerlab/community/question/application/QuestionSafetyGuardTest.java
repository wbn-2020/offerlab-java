package com.offerlab.community.question.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionSafetyGuardTest {

    @Test
    void questionInitSqlMustNotDropTables() throws Exception {
        String sql = Files.readString(Path.of("../db/init/10_question.sql"), StandardCharsets.UTF_8).toLowerCase();

        assertFalse(sql.contains("drop table"), "question init SQL must be migration-safe and never drop existing data");
        assertTrue(sql.contains("create table if not exists t_user_prep_target"));
        assertTrue(sql.contains("mistake_reason"), "question progress must keep the review reason column in init SQL");
        assertTrue(sql.contains("answer_draft"), "question progress must keep personal answer draft separately from note");
        assertTrue(sql.contains("star_story"), "question progress must keep STAR story mapping separately from note");
        assertTrue(sql.contains("exam_point"), "question init SQL must keep extracted exam point separately");
        assertTrue(sql.contains("reference_answer"), "question init SQL must keep extracted reference answer separately");
        assertTrue(sql.contains("source_snippet"), "question init SQL must keep source snippets for reviewability");
        assertTrue(sql.contains("quality_reason"), "question init SQL must keep extraction quality reasons");
        assertTrue(sql.contains("next_review_at"), "question progress must persist explicit next review time");
        assertTrue(sql.contains("last_reviewed_at"), "question progress must persist last review completion time");
        assertTrue(sql.contains("review_count"), "question progress must persist personal review count");
        assertTrue(sql.contains("review_interval_days"), "question progress must persist the spaced review interval");
        assertTrue(sql.contains("idx_uid_update_time"), "recent personal prep queries need a uid/update_time index");
        assertTrue(sql.contains("idx_uid_next_review"), "today review plan needs a uid/next_review_at index");
        assertTrue(sql.contains("idx_uid_status_next_review"), "status-based review plan queries need a status/next review index");
        assertTrue(sql.contains("idx_uid_status_question_time"), "personal progress filters need a status/question covering index");
        assertTrue(sql.contains("idx_uid_reason_question_time"), "personal mistake filters need a reason/question covering index");
        assertTrue(sql.contains("idx_normalized_status"), "cross-post question dedup needs a normalized hash lookup index");
    }

    @Test
    void prepReviewPlanMustPersistExplicitScheduleFields() throws Exception {
        String dtoSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/api/dto/QuestionDTO.java"), StandardCharsets.UTF_8);
        String poSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/infrastructure/persistence/po/UserQuestionProgressPO.java"), StandardCharsets.UTF_8);
        String mapperSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/infrastructure/persistence/mapper/UserQuestionProgressMapper.java"), StandardCharsets.UTF_8);
        String facadeSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/QuestionFacadeImpl.java"), StandardCharsets.UTF_8);
        String migrationSql = Files.readString(Path.of("../db/migration/20260529_question_review_schedule.sql"), StandardCharsets.UTF_8).toLowerCase();

        for (String field : List.of("nextReviewAt", "lastReviewedAt", "reviewCount", "reviewIntervalDays")) {
            assertTrue(dtoSource.contains(field), "question DTO must expose " + field);
            assertTrue(poSource.contains(field), "question progress PO must carry " + field);
            assertTrue(facadeSource.contains(field), "question facade must map " + field);
        }
        assertTrue(mapperSource.contains("next_review_at"), "progress mapper must persist next review time");
        assertTrue(mapperSource.contains("last_reviewed_at"), "progress mapper must persist last reviewed time");
        assertTrue(mapperSource.contains("review_count"), "progress mapper must persist review count");
        assertTrue(mapperSource.contains("review_interval_days"), "progress mapper must persist review interval");
        assertTrue(mapperSource.contains("updateStatusSchedule"), "progress status updates must update review schedule in one DB write");
        assertTrue(mapperSource.contains("up.next_review_at IS NOT NULL AND up.next_review_at <= NOW(3)"), "review plan must prefer explicit due review time");
        assertTrue(mapperSource.contains("up.next_review_at IS NULL AND up.progress_status = 'review'"), "review plan must keep fallback compatibility for old rows");
        assertTrue(facadeSource.contains("nextReviewSchedule"), "facade must calculate review schedule from progress transitions");
        assertTrue(facadeSource.contains("now.plusDays(1)"), "learning questions should be scheduled for near-term review");
        assertTrue(facadeSource.contains("now.plusDays(nextInterval)"), "mastered questions should move to spaced review");
        assertTrue(migrationSql.contains("next_review_at"), "existing databases must receive next review time non-destructively");
        assertTrue(migrationSql.contains("idx_uid_next_review"), "existing databases must receive review due index non-destructively");
        assertFalse(migrationSql.contains("drop table"), "review schedule migration must not drop data");
    }

    @Test
    void prepTargetsMustSupportInterviewDatePriorityAndNote() throws Exception {
        String cmdSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/api/dto/PrepTargetCmd.java"), StandardCharsets.UTF_8);
        String dtoSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/api/dto/PrepTargetDTO.java"), StandardCharsets.UTF_8);
        String poSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/infrastructure/persistence/po/UserPrepTargetPO.java"), StandardCharsets.UTF_8);
        String mapperSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/infrastructure/persistence/mapper/UserPrepTargetMapper.java"), StandardCharsets.UTF_8);
        String facadeSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/QuestionFacadeImpl.java"), StandardCharsets.UTF_8);
        String initSql = Files.readString(Path.of("../db/init/10_question.sql"), StandardCharsets.UTF_8).toLowerCase();
        String migrationSql = Files.readString(Path.of("../db/migration/20260530_prep_target_schedule.sql"), StandardCharsets.UTF_8).toLowerCase();

        for (String field : List.of("interviewDate", "priority", "note")) {
            assertTrue(cmdSource.contains(field), "prep target command must accept " + field);
            assertTrue(dtoSource.contains(field), "prep target DTO must expose " + field);
            assertTrue(poSource.contains(field), "prep target PO must persist " + field);
            assertTrue(facadeSource.contains(field), "prep target facade must map " + field);
        }
        assertTrue(cmdSource.contains("@Pattern(regexp = \"low|medium|high|urgent\")"), "target priority must be constrained");
        assertTrue(cmdSource.contains("@Size(max = 300)"), "target note must be length-limited");
        assertTrue(mapperSource.contains("interview_date"), "prep target mapper must read and write interview date");
        assertTrue(mapperSource.contains("CASE priority"), "prep targets should be sorted by priority after date");
        assertTrue(mapperSource.contains("ON DUPLICATE KEY UPDATE"), "adding an existing target should update schedule metadata");
        assertTrue(facadeSource.contains("normalizeTargetPriority"), "facade must normalize target priority");
        assertTrue(initSql.contains("interview_date"), "init SQL must include prep target interview date");
        assertTrue(initSql.contains("idx_uid_interview_priority"), "init SQL must include prep target date/priority index");
        assertTrue(migrationSql.contains("interview_date"), "existing databases must receive interview_date non-destructively");
        assertTrue(migrationSql.contains("idx_uid_interview_priority"), "existing databases must receive target schedule index non-destructively");
        assertFalse(migrationSql.contains("drop table"), "prep target migration must not drop data");
    }

    @Test
    void aiExtractedQuestionsMustExposeStructuredFieldsEndToEnd() throws Exception {
        String dtoSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/api/dto/QuestionDTO.java"), StandardCharsets.UTF_8);
        String poSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/infrastructure/persistence/po/InterviewQuestionPO.java"), StandardCharsets.UTF_8);
        String extractedSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/ExtractedQuestion.java"), StandardCharsets.UTF_8);
        String facadeSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/QuestionFacadeImpl.java"), StandardCharsets.UTF_8);
        String mapperSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/infrastructure/persistence/mapper/InterviewQuestionMapper.java"), StandardCharsets.UTF_8);
        String deepseekSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/DeepseekQuestionExtractor.java"), StandardCharsets.UTF_8);
        String ruleSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/RuleBasedQuestionExtractor.java"), StandardCharsets.UTF_8);
        String indexerSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/QuestionSearchIndexer.java"), StandardCharsets.UTF_8);
        String esClientSource = Files.readString(Path.of("../community-infrastructure/src/main/java/com/offerlab/community/infra/es/client/ElasticsearchHttpClient.java"), StandardCharsets.UTF_8);
        String migrationSql = Files.readString(Path.of("../db/migration/20260529_question_structured_fields.sql"), StandardCharsets.UTF_8).toLowerCase();

        for (String field : List.of("examPoint", "referenceAnswer", "sourceSnippet", "qualityReason")) {
            assertTrue(dtoSource.contains(field), "question DTO must expose " + field);
            assertTrue(poSource.contains(field), "question persistence object must carry " + field);
            assertTrue(extractedSource.contains(field), "extracted question must carry " + field);
            assertTrue(facadeSource.contains(field), "question facade must map " + field);
            assertTrue(deepseekSource.contains(field), "AI extractor prompt/parser must request " + field);
            assertTrue(ruleSource.contains(field), "rule fallback extractor must create " + field);
            assertTrue(indexerSource.contains(field), "question search index must include " + field);
        }
        assertTrue(mapperSource.contains("exam_point"), "admin mapper must persist exam point");
        assertTrue(mapperSource.contains("reference_answer"), "admin mapper must persist reference answer");
        assertTrue(mapperSource.contains("source_snippet"), "admin mapper must persist source snippet");
        assertTrue(mapperSource.contains("quality_reason"), "admin mapper must persist quality reason");
        assertTrue(esClientSource.contains("updateMapping"), "existing strict ES indexes must be able to receive new structured fields");
        assertTrue(indexerSource.contains("ensureStructuredFieldMapping"), "question indexer must update existing mapping before indexing new structured fields");
        assertTrue(migrationSql.contains("exam_point"), "existing databases must receive exam point non-destructively");
        assertTrue(migrationSql.contains("reference_answer"), "existing databases must receive reference answer non-destructively");
        assertTrue(migrationSql.contains("source_snippet"), "existing databases must receive source snippet non-destructively");
        assertTrue(migrationSql.contains("quality_reason"), "existing databases must receive quality reason non-destructively");
        assertFalse(migrationSql.contains("drop table"), "structured question migration must not drop data");
    }

    @Test
    void questionSearchMustReturnHighlightFieldsSafely() throws Exception {
        String dtoSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/api/dto/QuestionDTO.java"), StandardCharsets.UTF_8);
        String indexerSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/QuestionSearchIndexer.java"), StandardCharsets.UTF_8);
        String facadeSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/QuestionFacadeImpl.java"), StandardCharsets.UTF_8);

        assertTrue(dtoSource.contains("highlightQuestionText"), "question DTO must expose highlighted question text");
        assertTrue(dtoSource.contains("highlightExamPoint"), "question DTO must expose highlighted exam point");
        assertTrue(dtoSource.contains("highlightAnswerHint"), "question DTO should expose highlighted answer hint snippets");
        assertTrue(indexerSource.contains("body.put(\"highlight\""), "question ES search must request highlighted fields");
        assertTrue(indexerSource.contains("\"pre_tags\", List.of(\"<em>\")"), "question ES highlight must use stable em tags for frontend sanitization");
        assertTrue(indexerSource.contains("\"questionText\", Map.of(\"number_of_fragments\", 0)"), "question text highlight should return the full highlighted field");
        assertTrue(indexerSource.contains("\"examPoint\", Map.of(\"number_of_fragments\", 0)"), "exam point highlight should return the full highlighted field");
        assertTrue(indexerSource.contains("record QuestionSearchHit"), "question ES search must carry highlight snippets with ids");
        assertTrue(indexerSource.contains("firstHighlight(hit, \"questionText\")"), "question text highlight must be extracted from ES response");
        assertTrue(indexerSource.contains("firstHighlight(hit, \"examPoint\")"), "exam point highlight must be extracted from ES response");
        assertTrue(facadeSource.contains("Map<Long, QuestionSearchIndexer.QuestionSearchHit> highlights"), "facade must preserve ES highlight snippets beside DB rows");
        assertTrue(facadeSource.contains(".highlightQuestionText(hit == null ? null : hit.highlightQuestionText())"), "facade must map highlighted question text into DTO");
        assertTrue(facadeSource.contains(".highlightExamPoint(hit == null ? null : hit.highlightExamPoint())"), "facade must map highlighted exam point into DTO");
    }

    @Test
    void extractedQuestionsMustRefreshCanonicalGroupsAcrossPosts() throws Exception {
        String facadeSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/QuestionFacadeImpl.java"), StandardCharsets.UTF_8);
        String mapperSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/infrastructure/persistence/mapper/InterviewQuestionMapper.java"), StandardCharsets.UTF_8);
        String migrationSql = Files.readString(Path.of("../db/migration/20260529_question_canonical_index.sql"), StandardCharsets.UTF_8).toLowerCase();

        assertTrue(mapperSource.contains("selectCanonicalIdByHash"), "newly extracted duplicate questions must attach to an existing canonical group");
        assertTrue(mapperSource.contains("countVisibleSourcesByHash"), "canonical appear count must be based on visible source posts");
        assertTrue(mapperSource.contains("updateCanonicalGroup"), "canonical group rows must be updated together");
        assertTrue(mapperSource.contains("selectVisibleByHash"), "changed canonical groups must be re-indexed after frequency updates");
        assertTrue(mapperSource.contains("ORDER BY (q.canonical_id IS NULL) DESC"), "canonical refresh must re-elect a visible root when the old root disappears");
        assertTrue(facadeSource.contains("Set<String> changedHashes = oldQuestions.stream()"), "re-extraction must refresh old hash groups when a post drops a question");
        assertTrue(facadeSource.contains("Set<String> changedHashes = questions.stream()"), "hiding a source post must refresh affected canonical groups");
        assertTrue(facadeSource.contains("Set<String> affectedCompanies = oldQuestions.stream()"), "re-extraction must evict company prep caches for old source companies");
        assertTrue(facadeSource.contains("Set<String> affectedCompanies = questions.stream()"), "hiding source questions must evict company prep caches for old source companies");
        assertTrue(facadeSource.contains("po.setCanonicalId(questionMapper.selectCanonicalIdByHash(normalizedHash))"), "inserted questions must preserve canonical linkage immediately");
        assertTrue(facadeSource.contains("changedHashes.forEach(this::refreshCanonicalGroup)"), "extraction must refresh canonical appear counts after replacement");
        assertTrue(facadeSource.contains("affectedCompanies.forEach(this::evictQuestionCachesByCompany)"), "company prep caches must be evicted after question replacement or hiding");
        assertTrue(facadeSource.contains("questionMapper.updateCanonicalGroup(hash, canonicalId, appearCount)"), "canonical refresh must write grouped appear counts");
        assertTrue(facadeSource.contains("questionSearchIndexer.indexQuestion(row.getId())"), "updated frequency values must be reflected in the search index");
        assertTrue(migrationSql.contains("idx_normalized_status"), "existing databases must receive the normalized hash index non-destructively");
        assertFalse(migrationSql.contains("drop table"), "canonical migration must not drop data");
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

    @Test
    void questionListMustSupportAnyMistakeReasonFilterOnlyForQuery() throws Exception {
        String facadeSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/QuestionFacadeImpl.java"), StandardCharsets.UTF_8);
        String mapperSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/infrastructure/persistence/mapper/InterviewQuestionMapper.java"), StandardCharsets.UTF_8);

        assertTrue(facadeSource.contains("\"any\".equals(value)"), "list query should allow any mistake reason shortcut");
        assertFalse(facadeSource.contains("List.of(\"concept\", \"project\", \"memory\", \"expression\", \"careless\", \"other\", \"any\")"), "note save must not accept the query-only any marker");
        assertTrue(mapperSource.contains("up.mistake_reason IS NOT NULL"), "any mistake reason filter must match all marked mistake reasons");
    }

    @Test
    void prepChecklistAssemblyMustStayOutOfFacade() throws Exception {
        String facadeSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/QuestionFacadeImpl.java"), StandardCharsets.UTF_8);
        String assemblerSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/QuestionPrepAssembler.java"), StandardCharsets.UTF_8);

        assertTrue(facadeSource.contains("QuestionPrepAssembler"), "facade should delegate prep package assembly");
        assertFalse(facadeSource.contains("private List<CompanyPrepDTO.ChecklistItemDTO> companyPrepChecklist"), "facade should not own checklist assembly details");
        assertFalse(facadeSource.contains("private int prepScore"), "facade should not own prep score calculation");
        assertTrue(assemblerSource.contains("companyPrepChecklist"), "assembler should own company prep checklist assembly");
        assertTrue(assemblerSource.contains("prepScore"), "assembler should own prep score calculation");
        assertTrue(assemblerSource.contains("nextActions"), "assembler should own company prep next action calculation");
    }

    @Test
    void questionNoteMustKeepAnswerDraftAndStarStoryAsSeparateFields() throws Exception {
        String dtoSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/api/dto/QuestionDTO.java"), StandardCharsets.UTF_8);
        String mapperSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/infrastructure/persistence/mapper/UserQuestionProgressMapper.java"), StandardCharsets.UTF_8);
        String controllerSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/controller/QuestionController.java"), StandardCharsets.UTF_8);

        assertTrue(dtoSource.contains("answerDraft"), "question DTO must expose personal answer draft");
        assertTrue(dtoSource.contains("starStory"), "question DTO must expose STAR story mapping");
        assertTrue(mapperSource.contains("answer_draft"), "mapper must persist answer draft separately");
        assertTrue(mapperSource.contains("star_story"), "mapper must persist STAR story separately");
        assertTrue(controllerSource.contains("@Size(max = 4000)"), "answer draft must have backend length validation");
        assertTrue(controllerSource.contains("@Size(max = 2000)"), "STAR story must have backend length validation");
    }

    @Test
    void questionNotePatchMustPreserveExistingCardFieldsWhenRequestOmitsThem() throws Exception {
        String facadeSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/QuestionFacadeImpl.java"), StandardCharsets.UTF_8);

        assertTrue(facadeSource.contains("note == null && existing != null ? existing.getNote()"), "omitted note must keep the existing note");
        assertTrue(facadeSource.contains("mistakeReason == null && existing != null ? existing.getMistakeReason()"), "omitted mistake reason must keep the existing reason");
        assertTrue(facadeSource.contains("answerDraft == null && existing != null ? existing.getAnswerDraft()"), "omitted answer draft must keep the existing answer card");
        assertTrue(facadeSource.contains("starStory == null && existing != null ? existing.getStarStory()"), "omitted STAR story must keep the existing story mapping");
        assertTrue(facadeSource.contains("normalizedAnswer == null ? \"\" : normalizedAnswer"), "note response must remain null-safe for omitted answer drafts");
        assertTrue(facadeSource.contains("normalizedStory == null ? \"\" : normalizedStory"), "note response must remain null-safe for omitted STAR stories");
    }

    @Test
    void prepOverviewMustExposeRecentAnswerDraftCards() throws Exception {
        String overviewSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/api/dto/UserPrepOverviewDTO.java"), StandardCharsets.UTF_8);
        String mapperSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/infrastructure/persistence/mapper/UserQuestionProgressMapper.java"), StandardCharsets.UTF_8);
        String facadeSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/QuestionFacadeImpl.java"), StandardCharsets.UTF_8);

        assertTrue(overviewSource.contains("answerDraftQuestions"), "prep overview must expose recent answer draft cards");
        assertTrue(overviewSource.contains("noteCount"), "prep overview must expose personal note count");
        assertTrue(overviewSource.contains("answerDraftCount"), "prep overview must expose answer draft count");
        assertTrue(mapperSource.contains("selectRecentAnswerDrafts"), "mapper must query recent answer draft cards");
        assertTrue(mapperSource.contains("noteCount"), "overview count must include personal note count");
        assertTrue(mapperSource.contains("answerDraftCount"), "overview count must include answer draft card count");
        assertTrue(mapperSource.contains("up.answer_draft IS NOT NULL"), "answer draft query must require drafted content");
        assertTrue(mapperSource.contains("p.visibility = 1"), "answer draft query must keep source visibility aligned");
        assertTrue(facadeSource.contains(".noteCount(asLong(overviewCounts.get(\"noteCount\")))"), "facade must map personal note count");
        assertTrue(facadeSource.contains("selectRecentAnswerDrafts"), "facade must include answer draft cards in overview");
    }

    @Test
    void prepOverviewMustExposeWeaknessTagFocus() throws Exception {
        String overviewSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/api/dto/UserPrepOverviewDTO.java"), StandardCharsets.UTF_8);
        String mapperSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/infrastructure/persistence/mapper/UserQuestionProgressMapper.java"), StandardCharsets.UTF_8);
        String facadeSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/QuestionFacadeImpl.java"), StandardCharsets.UTF_8);

        assertTrue(overviewSource.contains("focusTagCounts"), "prep overview must expose weak tag focus counts");
        assertTrue(overviewSource.contains("FocusTagCountDTO"), "prep overview must carry a typed weak tag DTO");
        assertTrue(mapperSource.contains("countWeaknessTags"), "mapper must aggregate weak tags from review candidates");
        assertTrue(mapperSource.contains("COUNT(DISTINCT up.question_id)"), "weak tag aggregation should avoid duplicate question counts");
        assertTrue(mapperSource.contains("up.progress_status = 'review'"), "weak tag focus must include review questions");
        assertTrue(mapperSource.contains("up.mistake_reason IS NOT NULL"), "weak tag focus must include marked mistake reasons");
        assertTrue(facadeSource.contains(".focusTagCounts(focusTagCounts(uid))"), "facade must map weak tag focus into prep overview");
    }

    @Test
    void prepTargetSummaryMustUseRealTargetCounts() throws Exception {
        String facadeSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/QuestionFacadeImpl.java"), StandardCharsets.UTF_8);
        String questionMapperSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/infrastructure/persistence/mapper/InterviewQuestionMapper.java"), StandardCharsets.UTF_8);
        String progressMapperSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/infrastructure/persistence/mapper/UserQuestionProgressMapper.java"), StandardCharsets.UTF_8);

        assertTrue(questionMapperSource.contains("countPublicByTarget"), "target summaries must count the full matching question set");
        assertTrue(progressMapperSource.contains("countByTarget"), "target summaries must count personal progress across the full target");
        assertTrue(facadeSource.contains("questionMapper.countPublicByTarget"), "facade must not reuse a limited recommendation list as questionCount");
        assertTrue(facadeSource.contains("progressMapper.countByTarget"), "facade must not reuse a limited recommendation list as progress counts");
        assertFalse(facadeSource.contains("prepAssembler.targetSummary(target, ids.size()"), "target questionCount must not be capped by recommendation sample size");
    }

    @Test
    void companyPrepProgressMustUseFullCompanyCounts() throws Exception {
        String facadeSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/QuestionFacadeImpl.java"), StandardCharsets.UTF_8);
        String progressMapperSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/infrastructure/persistence/mapper/UserQuestionProgressMapper.java"), StandardCharsets.UTF_8);

        assertTrue(progressMapperSource.contains("countByCompany"), "company prep progress must aggregate all visible company questions");
        assertTrue(progressMapperSource.contains("AND q.company = #{company}"), "company prep progress must use the same exact company scope as the prep pack");
        assertTrue(facadeSource.contains("progressMapper.countByCompany(viewerUid, canonical)"), "company prep must not derive myProgress from the limited top question list");
        assertFalse(facadeSource.contains("topQuestions.stream().map(QuestionDTO::getId).toList()"), "company prep myProgress must not be capped by topQuestions");
    }

    @Test
    void companyPrepMustExposeInterviewResultTrends() throws Exception {
        String dtoSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/api/dto/CompanyPrepDTO.java"), StandardCharsets.UTF_8);
        String facadeSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/QuestionFacadeImpl.java"), StandardCharsets.UTF_8);
        String postMapperSource = Files.readString(Path.of("../community-domain-post/src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/PostMapper.java"), StandardCharsets.UTF_8);
        String postSql = Files.readString(Path.of("../db/init/02_post.sql"), StandardCharsets.UTF_8).toLowerCase();

        assertTrue(dtoSource.contains("interviewResultDistribution"), "company prep DTO must expose total interview result distribution");
        assertTrue(dtoSource.contains("recentResultDistribution"), "company prep DTO must expose recent interview result distribution");
        assertTrue(postMapperSource.contains("countInterviewResultsByCompany"), "company prep must aggregate interview result trends from real interview posts");
        assertTrue(postMapperSource.contains("AND e.company = #{company}"), "result trend aggregation must be scoped to the selected company");
        assertTrue(postMapperSource.contains("AND (#{since} IS NULL OR p.create_time >= #{since})"), "result trend aggregation must support recent windows");
        assertTrue(facadeSource.contains("toInterviewResultCounts("), "facade must translate raw result codes before returning company prep data");
        assertTrue(facadeSource.contains(".interviewResultDistribution(interviewResultDistribution)"), "facade must map total result distribution into CompanyPrepDTO");
        assertTrue(facadeSource.contains(".recentResultDistribution(recentResultDistribution)"), "facade must map recent result distribution into CompanyPrepDTO");
        assertTrue(facadeSource.contains("case 1 -> \"已 offer\""), "result code 1 should be user-readable in the prep pack");
        assertTrue(facadeSource.contains("case 2 -> \"待结果\""), "result code 2 should be user-readable in the prep pack");
        assertTrue(facadeSource.contains("case 3 -> \"已挂\""), "result code 3 should be user-readable in the prep pack");
        assertTrue(facadeSource.contains("default -> \"未选择\""), "missing result codes should remain explicit instead of looking like missing data");
        assertTrue(postSql.contains("interview_result"), "post extension table must keep the generated interview result column");
        assertTrue(postSql.contains("idx_company_result"), "company result trend queries need the company/result index");
    }

    @Test
    void companyPrepMustExposeDataConfidenceSamples() throws Exception {
        String dtoSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/api/dto/CompanyPrepDTO.java"), StandardCharsets.UTF_8);
        String facadeSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/QuestionFacadeImpl.java"), StandardCharsets.UTF_8);
        String questionMapperSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/infrastructure/persistence/mapper/InterviewQuestionMapper.java"), StandardCharsets.UTF_8);
        String postMapperSource = Files.readString(Path.of("../community-domain-post/src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/PostMapper.java"), StandardCharsets.UTF_8);

        for (String field : List.of("questionSampleCount", "postSampleCount", "resultSampleCount", "recentResultSampleCount", "dataUpdatedAt")) {
            assertTrue(dtoSource.contains(field), "company prep DTO must expose " + field);
            assertTrue(facadeSource.contains(field), "company prep facade must map " + field);
        }
        assertTrue(questionMapperSource.contains("summarizeCompanyQuestions"), "company prep must count the full visible company question sample");
        assertTrue(questionMapperSource.contains("MAX(q.update_time) AS updatedAt"), "question sample summary must expose real data freshness");
        assertTrue(postMapperSource.contains("summarizeInterviewPostsByCompany"), "company prep must count the full visible company post sample");
        assertTrue(postMapperSource.contains("MAX(p.update_time) AS updatedAt"), "post sample summary must expose real data freshness");
        assertTrue(facadeSource.contains("sumCounts(interviewResultDistribution)"), "result sample count should come from returned distribution data");
        assertTrue(facadeSource.contains("sumCounts(recentResultDistribution)"), "recent result sample count should come from returned distribution data");
        assertTrue(facadeSource.contains("maxTime(asLocalDateTime(questionSummary.get(\"updatedAt\")), asLocalDateTime(postSummary.get(\"updatedAt\")))"), "company prep must report the freshest known source update time");
    }

    @Test
    void companyPrepMustExposePersonalizedRecommendationsForLoggedInUsers() throws Exception {
        String dtoSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/api/dto/CompanyPrepDTO.java"), StandardCharsets.UTF_8);
        String facadeSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/QuestionFacadeImpl.java"), StandardCharsets.UTF_8);
        String progressMapperSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/infrastructure/persistence/mapper/UserQuestionProgressMapper.java"), StandardCharsets.UTF_8);

        assertTrue(dtoSource.contains("recommendedQuestions"), "company prep DTO must expose personalized question recommendations");
        assertTrue(facadeSource.contains("companyQuestionPool = questionMapper.selectTopByCompany(canonical, 20)"), "company prep should build recommendations from a wider company question pool");
        assertTrue(facadeSource.contains("viewerUid == null ? List.of() : companyRecommendedQuestions"), "anonymous company prep must not leak personal recommendations");
        assertTrue(facadeSource.contains(".recommendedQuestions(recommendedQuestions)"), "facade must map personalized recommendations into CompanyPrepDTO");
        assertTrue(facadeSource.contains("selectByUserAndQuestions(viewerUid, ids)"), "recommendations must use the viewer's real progress state");
        assertTrue(progressMapperSource.contains("selectByUserAndQuestions"), "progress mapper must support batch lookup for recommendation personalization");
        assertTrue(facadeSource.contains("!\"mastered\".equals(progress.getProgressStatus())"), "recommendations should not ask users to redo already mastered questions");
        assertTrue(facadeSource.contains("case \"review\" -> 0"), "due review questions should be recommended first");
        assertTrue(facadeSource.contains("case \"learning\" -> 1"), "learning questions should be recommended before untouched questions");
        assertTrue(facadeSource.contains(".limit(5)"), "company prep should keep the personal recommendation list focused");
    }

    @Test
    void extractionCompletionMustNotifyPostAuthor() throws Exception {
        String eventSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/api/event/QuestionExtractionFinishedEvent.java"), StandardCharsets.UTF_8);
        String facadeSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/QuestionFacadeImpl.java"), StandardCharsets.UTF_8);
        String notificationPom = Files.readString(Path.of("../community-domain-notification/pom.xml"), StandardCharsets.UTF_8);
        String listenerSource = Files.readString(Path.of("../community-domain-notification/src/main/java/com/offerlab/community/notification/application/QuestionNotificationListener.java"), StandardCharsets.UTF_8);
        String notificationFacade = Files.readString(Path.of("../community-domain-notification/src/main/java/com/offerlab/community/notification/api/NotificationFacade.java"), StandardCharsets.UTF_8);
        String notificationImpl = Files.readString(Path.of("../community-domain-notification/src/main/java/com/offerlab/community/notification/application/NotificationFacadeImpl.java"), StandardCharsets.UTF_8);

        assertTrue(eventSource.contains("postAuthorUid"), "question extraction completion event must carry the post author");
        assertTrue(eventSource.contains("questionCount"), "question extraction completion event must carry extracted question count");
        assertTrue(facadeSource.contains("publishExtractionFinished(task, post, true"), "successful extraction must publish a completion event");
        assertTrue(facadeSource.contains("publishExtractionFinished(task, post, false"), "failed extraction must publish a failure event");
        assertTrue(notificationPom.contains("community-domain-question"), "notification module must listen to question domain events without question depending on notification");
        assertTrue(listenerSource.contains("QuestionExtractionFinishedEvent"), "notification module must listen for question extraction completion events");
        assertTrue(listenerSource.contains("question_extract_succeeded"), "success notifications need a stable action code");
        assertTrue(listenerSource.contains("question_extract_failed"), "failure notifications need a stable action code");
        assertTrue(notificationFacade.contains("notifySystem"), "notification facade must expose system notifications");
        assertTrue(notificationImpl.contains("TYPE_SYSTEM"), "extraction notifications must use existing system notification type");
    }

    @Test
    void questionListMustSupportPersonalAnswerDraftFilter() throws Exception {
        String querySource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/api/dto/QuestionQuery.java"), StandardCharsets.UTF_8);
        String controllerSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/controller/QuestionController.java"), StandardCharsets.UTF_8);
        String facadeSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/QuestionFacadeImpl.java"), StandardCharsets.UTF_8);
        String mapperSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/infrastructure/persistence/mapper/InterviewQuestionMapper.java"), StandardCharsets.UTF_8);

        assertTrue(querySource.contains("hasNote"), "question query must carry note filter");
        assertTrue(querySource.contains("hasAnswerDraft"), "question query must carry answer draft filter");
        assertTrue(querySource.contains("hasStarStory"), "question query must carry STAR story filter");
        assertTrue(controllerSource.contains("Boolean hasNote"), "question list API must accept hasNote");
        assertTrue(controllerSource.contains("Boolean hasAnswerDraft"), "question list API must accept hasAnswerDraft");
        assertTrue(controllerSource.contains("Boolean hasStarStory"), "question list API must accept hasStarStory");
        assertTrue(facadeSource.contains("Boolean.TRUE.equals(q.getHasNote())"), "facade must normalize hasNote");
        assertTrue(facadeSource.contains("Boolean.TRUE.equals(q.getHasAnswerDraft())"), "facade must normalize hasAnswerDraft");
        assertTrue(facadeSource.contains("Boolean.TRUE.equals(q.getHasStarStory())"), "facade must normalize hasStarStory");
        assertTrue(facadeSource.contains("boolean usePersonalFilter"), "facade must collapse personal filter checks into a readable flag");
        assertTrue(facadeSource.contains("usePersonalFilter && viewerUid == null"), "anonymous personal filters must return an empty page");
        assertTrue(mapperSource.contains("<if test='usePersonalFilter'>"), "mapper must gate personal filters with one readable flag");
        assertTrue(mapperSource.contains("<if test='hasNote'>"), "mapper must conditionally apply note filter");
        assertTrue(mapperSource.contains("<if test='hasAnswerDraft'>"), "mapper must conditionally apply answer draft filter");
        assertTrue(mapperSource.contains("<if test='hasStarStory'>"), "mapper must conditionally apply STAR story filter");
        assertTrue(mapperSource.contains("up.note IS NOT NULL"), "note filter must match personal notes");
        assertTrue(mapperSource.contains("up.answer_draft IS NOT NULL"), "answer draft filter must match drafted answers");
        assertTrue(mapperSource.contains("up.star_story IS NOT NULL"), "answer draft filter must match STAR story mappings");
        assertTrue(mapperSource.indexOf("AND EXISTS (") == mapperSource.lastIndexOf("AND EXISTS ("), "personal filters should share one progress EXISTS block");
    }
}

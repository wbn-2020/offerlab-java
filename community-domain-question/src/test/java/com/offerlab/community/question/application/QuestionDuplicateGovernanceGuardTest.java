package com.offerlab.community.question.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionDuplicateGovernanceGuardTest {

    @Test
    void adminQuestionsMustExposeDuplicateGroupGovernance() throws Exception {
        String dtoSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/api/dto/QuestionDuplicateGroupDTO.java"), StandardCharsets.UTF_8);
        String facadeApiSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/QuestionFacade.java"), StandardCharsets.UTF_8);
        String facadeSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/QuestionFacadeImpl.java"), StandardCharsets.UTF_8);
        String mapperSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/infrastructure/persistence/mapper/InterviewQuestionMapper.java"), StandardCharsets.UTF_8);
        String controllerSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/controller/QuestionAdminController.java"), StandardCharsets.UTF_8);

        assertTrue(dtoSource.contains("normalizedHash"), "duplicate group DTO must expose normalized hash for explainability");
        assertTrue(dtoSource.contains("sourcePostCount"), "duplicate group DTO must expose source post count");
        assertTrue(dtoSource.contains("List<QuestionDTO> questions"), "duplicate group DTO must carry grouped questions");
        assertTrue(dtoSource.contains("semanticCandidates"), "duplicate group DTO must expose cross-hash semantic candidates");
        assertTrue(Files.readString(Path.of("src/main/java/com/offerlab/community/question/api/dto/QuestionDuplicateCandidateDTO.java"), StandardCharsets.UTF_8)
                .contains("similarityScore"), "semantic candidate DTO must expose a similarity score");

        assertTrue(facadeApiSource.contains("getDuplicateGroup"), "question facade must expose duplicate group query");
        assertTrue(facadeApiSource.contains("setDuplicateCanonical"), "question facade must expose canonical adjustment");
        assertTrue(facadeApiSource.contains("mergeDuplicateCandidate"), "question facade must expose semantic candidate merge");
        assertTrue(facadeApiSource.contains("hideDuplicateQuestions"), "question facade must expose duplicate hiding");
        assertTrue(mapperSource.contains("selectAdminByHash"), "mapper must list admin duplicate group rows by normalized hash");
        assertTrue(mapperSource.contains("selectSemanticDuplicateCandidates"), "mapper must list cross-hash semantic duplicate candidates");
        assertTrue(mapperSource.contains("updateCanonicalId"), "mapper must allow merging a semantic candidate into a canonical group");
        assertTrue(mapperSource.contains("updateAdminCanonicalGroup"), "mapper must update canonical id across the group");

        assertTrue(controllerSource.contains("/questions/{id}/duplicates"), "admin controller must expose duplicate group endpoint");
        assertTrue(controllerSource.contains("/questions/{id}/duplicates/canonical"), "admin controller must expose canonical endpoint");
        assertTrue(controllerSource.contains("/questions/{id}/duplicates/merge-candidate"), "admin controller must expose semantic candidate merge endpoint");
        assertTrue(controllerSource.contains("/questions/{id}/duplicates/hide"), "admin controller must expose hide endpoint");
        assertTrue(controllerSource.contains("QUESTION_DUPLICATE_CANONICAL"), "canonical changes must be audited");
        assertTrue(controllerSource.contains("QUESTION_DUPLICATE_MERGE_CANDIDATE"), "semantic candidate merges must be audited");
        assertTrue(controllerSource.contains("QUESTION_DUPLICATE_HIDE"), "duplicate hide changes must be audited");

        assertTrue(facadeSource.contains("!Objects.equals(question.getNormalizedHash(), canonical.getNormalizedHash())"), "canonical adjustment must reject cross-group questions");
        assertTrue(facadeSource.contains("semanticSimilarityScore(question, candidate)"), "semantic merges must validate the candidate similarity");
        assertTrue(facadeSource.contains("similarityScore < 72"), "semantic merges must reject weak candidates");
        assertTrue(facadeSource.contains("TECHNICAL_KEYWORDS"), "semantic candidates must use technical keyword signals");
        assertTrue(facadeSource.contains("QUESTION_APPROVED"), "canonical adjustment must require an approved main question");
        assertTrue(facadeSource.contains("refreshCanonicalGroup(question.getNormalizedHash())"), "hiding duplicates must refresh canonical counts");
        assertTrue(facadeSource.contains("questionSearchIndexer.indexQuestion"), "duplicate governance must sync search index");
    }
}

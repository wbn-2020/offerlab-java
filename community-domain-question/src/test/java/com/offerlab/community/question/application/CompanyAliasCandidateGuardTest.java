package com.offerlab.community.question.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyAliasCandidateGuardTest {

    @Test
    void companyAliasOpsMustRecommendCandidatesFromQuestionAndPostCompanies() throws Exception {
        String dtoSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/api/dto/CompanyAliasCandidateDTO.java"), StandardCharsets.UTF_8);
        String facadeApiSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/QuestionFacade.java"), StandardCharsets.UTF_8);
        String facadeSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/QuestionFacadeImpl.java"), StandardCharsets.UTF_8);
        String controllerSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/controller/QuestionAdminController.java"), StandardCharsets.UTF_8);
        String questionMapperSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/infrastructure/persistence/mapper/InterviewQuestionMapper.java"), StandardCharsets.UTF_8);
        String postMapperSource = Files.readString(Path.of("../community-domain-post/src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/PostMapper.java"), StandardCharsets.UTF_8);

        assertTrue(dtoSource.contains("canonicalCompany"), "candidate DTO must expose suggested canonical company");
        assertTrue(dtoSource.contains("alias"), "candidate DTO must expose suggested alias");
        assertTrue(dtoSource.contains("questionSampleCount"), "candidate DTO must expose question sample count");
        assertTrue(dtoSource.contains("postSampleCount"), "candidate DTO must expose post sample count");
        assertTrue(dtoSource.contains("reason"), "candidate DTO must explain why it was recommended");

        assertTrue(facadeApiSource.contains("listCompanyAliasCandidates"), "question facade must expose alias candidates");
        assertTrue(controllerSource.contains("/company-aliases/candidates"), "admin controller must expose alias candidate endpoint");
        assertTrue(controllerSource.contains("ROLE_QUESTION_OPERATOR"), "alias candidate endpoint must stay behind question ops role");

        assertTrue(questionMapperSource.contains("countCompaniesForAliasCandidates"), "candidate generation must aggregate question companies");
        assertTrue(postMapperSource.contains("countInterviewCompaniesForAliasCandidates"), "candidate generation must aggregate interview post companies");
        assertTrue(facadeSource.contains("aggregateCompanyNameStats"), "facade must merge company stats from multiple sources");
        assertTrue(facadeSource.contains("knownCompanyCanonical"), "facade must know common bilingual company aliases");
        assertTrue(facadeSource.contains("命中常见中英文/简称映射"), "candidate reason must explain known alias hits");
        assertTrue(facadeSource.contains("名称包含关系，疑似简称或历史名称"), "candidate reason must explain containment hits");
        assertTrue(facadeSource.contains("existingAliases"), "candidate generation must skip aliases that already exist");
    }

    @Test
    void companyPrepCacheInvalidationMustWaitForTransactionCommit() throws Exception {
        String facadeSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/application/QuestionFacadeImpl.java"), StandardCharsets.UTF_8);

        assertTrue(facadeSource.contains("private final AfterCommitExecutor afterCommit;"),
                "company-prep mutations must use the shared after-commit executor");
        assertTrue(facadeSource.contains(
                        "scheduleCompanyCacheEvictions(affectedCompanies, \"company alias cache:\" + po.getId());"),
                "company-alias writes must defer cache eviction");
        assertTrue(facadeSource.contains(
                        "scheduleCompanyCacheEvictions(affectedCompanies, \"company alias status cache:\" + id);"),
                "company-alias status changes must defer cache eviction");
        assertTrue(facadeSource.contains(
                        "scheduleCompanyCacheEvictions(List.of(company), \"post question cache eviction:\" + post.getId());"),
                "post-update company cache eviction must defer until commit");
        assertTrue(facadeSource.contains("() -> normalized.forEach(this::evictQuestionCachesByCompany)"),
                "only the deferred task may execute company-prep cache eviction");
        assertFalse(facadeSource.contains("evictQuestionCachesByCompany(canonical);"),
                "company-alias writes must not evict cache before commit");
        assertFalse(facadeSource.contains("evictQuestionCachesByCompany(old.getCanonicalCompany());"),
                "company-alias status changes must not evict cache before commit");
    }
}

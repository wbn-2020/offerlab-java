package com.offerlab.community.question.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
}

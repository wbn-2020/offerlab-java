package com.offerlab.community.question.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionSyntheticFilterGuardTest {

    @Test
    void publicQuestionDtosMustFilterExplicitSyntheticContent() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/offerlab/community/question/application/QuestionFacadeImpl.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("!isSyntheticQuestion(row"),
                "public question DTO conversion must hide explicit synthetic question content");
        assertTrue(source.contains("PublicContentFilter.isSyntheticText(row.getQuestionText())"),
                "question text must be part of the synthetic filter");
        assertTrue(source.contains("QuestionTagDTO::getName"),
                "question tags must be part of the synthetic filter");
        assertTrue(source.contains("toAdminQuestionDtos"),
                "admin question views must keep an explicit path for test-data governance");
    }
}

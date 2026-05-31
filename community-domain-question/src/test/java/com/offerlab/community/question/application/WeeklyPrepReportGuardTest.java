package com.offerlab.community.question.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeeklyPrepReportGuardTest {

    @Test
    void weeklyPrepReportMustExposeSevenDayReviewAndMockInterviewSummary() throws Exception {
        String dto = read("src/main/java/com/offerlab/community/question/api/dto/UserWeeklyPrepReportDTO.java");
        String facadeApi = read("src/main/java/com/offerlab/community/question/application/QuestionFacade.java");
        String controller = read("src/main/java/com/offerlab/community/question/controller/QuestionController.java");
        String facade = read("src/main/java/com/offerlab/community/question/application/QuestionFacadeImpl.java");
        String progressMapper = read("src/main/java/com/offerlab/community/question/infrastructure/persistence/mapper/UserQuestionProgressMapper.java");
        String mockMapper = read("src/main/java/com/offerlab/community/question/infrastructure/persistence/mapper/MockInterviewSessionMapper.java");

        for (String field : new String[]{"windowStart", "windowEnd", "touchedQuestionCount", "masteredQuestionCount", "reviewQuestionCount", "noteCount", "answerDraftCount", "mockSessionCount", "mockCompletedCount", "mockAnsweredQuestionCount", "mockAverageScorePercent", "mockBestScorePercent", "nextActions"}) {
            assertTrue(dto.contains(field), "weekly report DTO must expose " + field);
        }
        assertTrue(facadeApi.contains("getMyWeeklyPrepReport"), "question facade must expose weekly prep report");
        assertTrue(controller.contains("/me/prep/weekly-report"), "question controller must expose weekly report endpoint");
        assertTrue(facade.contains("LocalDateTime since = now.minusDays(7)"), "weekly report must use the same 7-day window as the prep desk");
        assertTrue(facade.contains("progressMapper.summarizeWeeklyReport"), "weekly report must aggregate question progress");
        assertTrue(facade.contains("mockInterviewSessionMapper.summarizeWeeklyReport"), "weekly report must aggregate mock interview progress");
        assertTrue(facade.contains("weeklyNextActions"), "weekly report must include next-action suggestions");
        assertTrue(facade.contains("Objects.requireNonNullElse(progressMapper.countOverview(uid), Map.of())"), "empty prep overview aggregates must render as zero counts");
        assertTrue(facade.contains("Objects.requireNonNullElse(progressMapper.summarizeWeeklyReport(uid, since), Map.of())"), "empty progress weekly aggregates must render as zero counts");
        assertTrue(facade.contains("Objects.requireNonNullElse(mockInterviewSessionMapper.summarizeWeeklyReport(uid, since), Map.of())"), "empty mock interview weekly aggregates must render as zero counts");
        assertTrue(facade.contains("Objects.requireNonNullElse(progressMapper.countByTarget(viewerUid"), "empty target aggregates must render as zero counts");
        assertTrue(progressMapper.contains("summarizeWeeklyReport"), "progress mapper must summarize weekly touched questions");
        assertTrue(progressMapper.contains("up.update_time >= #{since}"), "progress weekly report must filter by update time");
        assertTrue(progressMapper.contains("t.tag_name"), "prep tag queries must use the real t_tag.tag_name column");
        assertFalse(progressMapper.contains("t.name"), "prep tag queries must not reference the nonexistent t_tag.name column");
        assertTrue(mockMapper.contains("summarizeWeeklyReport"), "mock interview mapper must summarize weekly sessions");
        assertTrue(mockMapper.contains("update_time >= #{since}"), "mock interview weekly report must filter by update time");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}

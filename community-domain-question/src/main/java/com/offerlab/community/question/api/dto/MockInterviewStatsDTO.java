package com.offerlab.community.question.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockInterviewStatsDTO {
    private Integer sessionCount;
    private Integer completedCount;
    private Integer totalQuestionCount;
    private Integer answeredQuestionCount;
    private Integer averageScorePercent;
    private Integer bestScorePercent;
    private Integer averageDurationSeconds;
    private Integer insightWindowSize;
    private MockInterviewSessionDTO lastSession;
    private List<MockInterviewSessionDTO> recentSessions;
    private List<MockInterviewAnswerDTO> weakAnswers;
    private List<InsightDTO> focusTagInsights;
    private List<InsightDTO> companyInsights;
    private List<InsightDTO> positionInsights;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InsightDTO {
        private String name;
        private Integer sessionCount;
        private Integer averageScorePercent;
        private Integer bestScorePercent;
        private Integer averageDurationSeconds;
    }
}

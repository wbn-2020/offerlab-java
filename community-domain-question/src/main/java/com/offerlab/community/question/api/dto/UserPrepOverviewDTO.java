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
public class UserPrepOverviewDTO {
    private Long favoriteCount;
    private Long todoCount;
    private Long learningCount;
    private Long masteredCount;
    private Long reviewCount;
    private Long noteCount;
    private Long answerDraftCount;
    private List<PrepTargetDTO> targets;
    private List<QuestionDTO> favoriteQuestions;
    private List<QuestionDTO> reviewQuestions;
    private List<QuestionDTO> answerDraftQuestions;
    private List<QuestionDTO> recommendedQuestions;
    private List<TargetPrepSummaryDTO> targetSummaries;
    private List<MistakeReasonCountDTO> mistakeReasonCounts;
    private List<FocusTagCountDTO> focusTagCounts;
    private ReviewPlanDTO reviewPlan;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TargetPrepSummaryDTO {
        private PrepTargetDTO target;
        private Integer questionCount;
        private Long favoriteCount;
        private Long learningCount;
        private Long masteredCount;
        private Long reviewCount;
        private List<QuestionDTO> recommendedQuestions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MistakeReasonCountDTO {
        private String reason;
        private Long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FocusTagCountDTO {
        private String name;
        private Long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewPlanDTO {
        private Integer todayCount;
        private Integer weekTouchedCount;
        private List<QuestionDTO> todayQuestions;
        private List<QuestionDTO> weekTouchedQuestions;
    }
}

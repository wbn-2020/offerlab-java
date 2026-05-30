package com.offerlab.community.question.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserWeeklyPrepReportDTO {
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private Integer touchedQuestionCount;
    private Integer masteredQuestionCount;
    private Integer reviewQuestionCount;
    private Integer noteCount;
    private Integer answerDraftCount;
    private Integer mockSessionCount;
    private Integer mockCompletedCount;
    private Integer mockAnsweredQuestionCount;
    private Integer mockAverageScorePercent;
    private Integer mockBestScorePercent;
    private List<UserPrepOverviewDTO.MistakeReasonCountDTO> mistakeReasonCounts;
    private List<UserPrepOverviewDTO.FocusTagCountDTO> focusTagCounts;
    private List<QuestionDTO> touchedQuestions;
    private List<String> nextActions;
}

package com.offerlab.community.question.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockInterviewAnswerDTO {
    private Long id;
    private Long sessionId;
    private Long questionId;
    private Integer sequenceNo;
    private String questionTextSnapshot;
    private String answerHintSnapshot;
    private String companySnapshot;
    private String positionSnapshot;
    private String roundSnapshot;
    private String difficultySnapshot;
    private String answerText;
    private String selfReview;
    private Integer score;
    private Boolean aiReviewed;
    private Integer aiScore;
    private String aiCompleteness;
    private String aiProjectExpression;
    private String aiFollowUpSuggestion;
    private String aiReviewProvider;
    private LocalDateTime createTime;
    private QuestionDTO question;
}

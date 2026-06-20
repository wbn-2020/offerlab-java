package com.offerlab.community.question.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_mock_interview_answer")
public class MockInterviewAnswerPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long sessionId;
    private Long uid;
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
    private Integer aiReviewed;
    private String aiReviewStatus;
    private String aiReviewError;
    private Integer aiScore;
    private String aiCompleteness;
    private String aiProjectExpression;
    private String aiFollowUpSuggestion;
    private String aiReviewProvider;
    private String aiReviewTaskId;
    private Integer aiReviewFallbackUsed;
    private Long aiReviewDurationMs;
    private Integer aiReviewPromptTokens;
    private Integer aiReviewCompletionTokens;
    private Long aiReviewEstimatedCostMicros;
    private String aiReviewErrorCode;
    private LocalDateTime createTime;
}

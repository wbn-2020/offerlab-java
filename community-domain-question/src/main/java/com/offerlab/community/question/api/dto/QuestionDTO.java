package com.offerlab.community.question.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionDTO {
    private Long id;
    private Long canonicalId;
    private String questionText;
    private String highlightQuestionText;
    private String answerHint;
    private String highlightAnswerHint;
    private String examPoint;
    private String highlightExamPoint;
    private String referenceAnswer;
    private String sourceSnippet;
    private String qualityReason;
    private String company;
    private String position;
    private String interviewRound;
    private String difficulty;
    private BigDecimal confidence;
    private Long sourcePostId;
    private Long sourceAuthorUid;
    private Integer status;
    private Integer appearCount;
    private Integer qualityScore;
    private List<QuestionTagDTO> tags;
    private Boolean favorite;
    private String progressStatus;
    private String note;
    private String mistakeReason;
    private String answerDraft;
    private String starStory;
    private LocalDateTime nextReviewAt;
    private LocalDateTime lastReviewedAt;
    private Integer reviewCount;
    private Integer reviewIntervalDays;
    private Integer sourcePostCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

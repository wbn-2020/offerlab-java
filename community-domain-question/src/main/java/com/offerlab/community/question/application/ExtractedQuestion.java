package com.offerlab.community.question.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedQuestion {
    private String questionText;
    private String answerHint;
    private String examPoint;
    private String referenceAnswer;
    private String sourceSnippet;
    private String qualityReason;
    private String company;
    private String position;
    private String interviewRound;
    private String difficulty;
    private BigDecimal confidence;
    private List<Long> tagIds;
}
